"""Generate and actually run max-limit cases (no pre-generated large inputs in ZIP)."""
import json
import pathlib
import subprocess
import time

P=pathlib.Path(__file__).resolve().parents[1]
JAVA=['java','-Xmx384m','-cp',str(P/'.build'/'Java'),'Main']
N=100_000
M=250_000

def execute(x, name, validate):
    line=json.dumps(x,separators=(',',':'))+'\n'
    start=time.perf_counter()
    completed=subprocess.run(JAVA,input=line,encoding='utf-8',capture_output=True,timeout=20)
    elapsed=time.perf_counter()-start
    if completed.returncode:
        raise AssertionError(f'{name} java exited {completed.returncode}: {completed.stderr[:500]}')
    answer=json.loads(completed.stdout)
    validate(answer)
    result=f'PASS {name}: n={x["n"]}, items={len(x.get("operations", x.get("edges", [])))}, wall_time_s={elapsed:.3f}'
    print(result,flush=True)
    return result

def require(cond,desc):
    if not cond:raise AssertionError(desc)

def main():
    summaries=[]
    ops=[[i,i+1] for i in range(N-1)]
    ops += [[i%(N-1),i%(N-1)+1] for i in range(M-len(ops))]
    def check_dsu(a):
        s=a['states']
        require(len(s)==M and s[0]==[N-1,2] and s[N-2]==[1,N]
                and s[-1]==[1,N],'dsu states incorrect')
    summaries.append(execute({'task':'dsu','n':N,'operations':ops},'DSU max',check_dsu))
    del ops
    weighted=[[i,i+1,0] for i in range(N-1)]
    weighted += [[i%(N-1),i%(N-1)+1,2] for i in range(M-len(weighted))]
    def check_forest(a):
        require(a['cost']==0 and a['components']==1 and len(a['edge_ids'])==N-1,'forest wrong')
        require(len(set(a['edge_ids']))==N-1 and all(i<N-1 for i in a['edge_ids']),'non-minimal edges')
    for task in ('kruskal','prim'):
        summaries.append(execute({'task':task,'n':N,'edges':weighted},task+' max',check_forest))
    directed=[[r[0],r[1]] for r in weighted]
    def check_scc(a):
        labels=a['component']
        require(len(labels)==N and len(set(labels))==N,'SCC count wrong')
        pairs=set(map(tuple,a['condensation']))
        require(len(pairs)==N-1 and pairs=={(labels[i],labels[i+1]) for i in range(N-1)},'condensation wrong')
    summaries.append(execute({'task':'scc','n':N,'edges':directed},'SCC max',check_scc))
    def check_topo(a):
        require(a['acyclic'] is True and a['order']==list(range(N)),'order wrong')
    summaries.append(execute({'task':'topo','n':N,'edges':directed},'topo max',check_topo))
    dag_edges=[[i,i+1,1] for i in range(N-1)]
    dag_edges += [[i%(N-1),i%(N-1)+1,2] for i in range(M-len(dag_edges))]
    def check_dag(a):
        require(a['acyclic'] is True and a['distance']==list(range(N)), 'distance wrong')
        require(a['parent_edge']==[-1]+list(range(N-1)),'parent IDs wrong')
    summaries.append(execute({'task':'dag','n':N,'source':0,'edges':dag_edges},'DAG max',check_dag))
    (P/'tests/scale_results.txt').write_text('\n'.join(summaries)+'\n')
if __name__=='__main__':main()
