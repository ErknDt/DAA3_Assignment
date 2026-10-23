"""Seeded differential tests with independent small-graph reference algorithms."""
import collections
import json
import random
import sys
from pathlib import Path
P=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(P/'tools'))
from run import build,execute
from public_validate import check
SEED=31415926
R=random.Random(SEED)

def components(n, edges):
    p=list(range(n))
    def find(a):
        while p[a]!=a:a=p[a]
        return a
    for u,v,*_ in edges:
        a,b=find(u),find(v)
        p[b]=a
    return len({find(i) for i in range(n)})

def brute_forest(n, edges):
    c=components(n,edges)
    required=n-c
    best=None
    for mask in range(1<<len(edges)):
        if mask.bit_count()!=required:continue
        p=list(range(n))
        def root(x):
            while p[x]!=x:x=p[x]
            return x
        cost=0
        valid=True
        for j,(u,v,w) in enumerate(edges):
            if mask&(1<<j):
                a,b=root(u),root(v)
                if a==b:valid=False;break
                p[b]=a
                cost+=w
        if valid and len({root(i) for i in range(n)})==c:
            best=cost if best is None else min(best,cost)
    assert best is not None
    return {'cost':best,'components':c,'edge_ids':[]}

def scc_reference(n,edges):
    reach=[[i==j for j in range(n)] for i in range(n)]
    for u,v in edges:reach[u][v]=True
    for k in range(n):
        for i in range(n):
            for j in range(n):
                if reach[i][k] and reach[k][j]:reach[i][j]=True
    labels=[None]*n
    for i in range(n):
        if labels[i] is not None: continue
        for j in range(n):
            if reach[i][j] and reach[j][i]:labels[j]=i
    return {'component':labels, 'condensation':[]}

def is_dag(n,edges):
    indegree=[0]*n
    nxt=[[] for _ in range(n)]
    for u,v,*_ in edges:
        indegree[v]+=1;nxt[u].append(v)
    todo=collections.deque(i for i in range(n) if indegree[i]==0)
    removed=0
    while todo:
        u=todo.popleft();removed+=1
        for v in nxt[u]:
            indegree[v]-=1
            if indegree[v]==0:todo.append(v)
    return removed==n

def dag_reference(n,edges,source):
    if not is_dag(n,edges):return {'acyclic':False,'distance':[],'parent_edge':[]}
    d=[None]*n;d[source]=0
    for _ in range(n-1):
        changed=False
        for u,v,w in edges:
            if d[u] is not None and (d[v] is None or d[u]+w<d[v]):
                d[v]=d[u]+w;changed=True
        if not changed:break
    return {'acyclic':True,'distance':d,'parent_edge':[]}

def dsu_reference(n,operations):
    a=[set([i]) for i in range(n)]
    answer=[]
    for u,v in operations:
        if a[u] is not a[v]:
            joined=a[u]|a[v]
            for k in joined:a[k]=joined
        unique={id(group):len(group) for group in a}
        answer.append([len(unique),max(unique.values(),default=0)])
    return {'states':answer}

def main():
    cases=[]; expected=[]; names=[]
    def add(x,e):cases.append(x);expected.append(e);names.append(x['task'])
    for _ in range(50):
        n=R.randrange(0,9)
        ops=[[R.randrange(n),R.randrange(n)] for _ in range(R.randrange(12))] if n else []
        add({'task':'dsu','n':n,'operations':ops},dsu_reference(n,ops))
    for _ in range(50):
        n=R.randrange(0,9)
        e=[[R.randrange(n),R.randrange(n),R.randrange(-7,8)] for _ in range(R.randrange(11))] if n else []
        ref=brute_forest(n,e)
        add({'task':'kruskal','n':n,'edges':e},ref)
        add({'task':'prim','n':n,'edges':e},ref)
    for _ in range(50):
        n=R.randrange(0,9)
        e=[[R.randrange(n),R.randrange(n)] for _ in range(R.randrange(16))] if n else []
        add({'task':'scc','n':n,'edges':e},scc_reference(n,e))
        add({'task':'topo','n':n,'edges':e},{'acyclic':is_dag(n,e),'order':[]})
    for _ in range(50):
        n=R.randrange(1,9)
        e=[[R.randrange(n),R.randrange(n),R.randrange(-7,8)] for _ in range(R.randrange(16))]
        source=R.randrange(n)
        add({'task':'dag','n':n,'edges':e,'source':source},dag_reference(n,e,source))
    answers=execute(build('Java'),cases)
    fails=[]
    for i,(x,y,e) in enumerate(zip(cases,answers,expected)):
        if not check(x,y,e): fails.append((i,x,y,e))
    if fails:
        raise AssertionError('FAILED randomized reference comparisons: '+repr(fails[:2]))
    result=f'PASS {len(cases)} seeded random differential cases; seed={SEED} (DSU=50, Kruskal=50, Prim=50, SCC=50, topo=50, DAG=50)'
    print(result)
    (P/'tests/randomized_results.txt').write_text(result+'\n')
if __name__=='__main__':main()
