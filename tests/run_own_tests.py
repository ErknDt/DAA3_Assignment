"""Run own sample cases, independently justified expected answers and witness mutations."""
import json
import sys
from pathlib import Path

P=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(P/'tools'))
from public_validate import check
from run import build, execute

def main():
    inputs=[json.loads(line) for line in (P/'tests/my_cases.jsonl').read_text().splitlines()]
    refs=json.loads((P/'tests/expected.json').read_text())
    assert len(refs)==len(inputs)
    answers=execute(build('Java'), inputs*2)
    for i,(x,e,y) in enumerate(zip(inputs,refs,answers[:len(inputs)]),1):
        if not check(x,y,e['output']):
            raise AssertionError(f'own case {i}: {e["reason"]}\nactual {y}')
    if not all(check(x,y,e['output']) for (x,e),y in zip(list(zip(inputs,refs))*2,answers)):
        raise AssertionError('state reset repeated mixed batch')
    # Semantic validator: accepted alternative witnesses, rejected invalid ones.
    forest=inputs[3]; optimum=refs[3]['output']
    assert check(forest, {'cost':-2,'components':2,'edge_ids':[4,3,2]}, optimum)
    assert not check(forest, {'cost':-2,'components':2,'edge_ids':[2,2,4]}, optimum)
    sc=inputs[7];scRef=refs[7]['output']
    assert check(sc, {'component':[7,7,9,9,11,13], 'condensation':[[7,9],[9,11]]},scRef)
    assert not check(sc, {'component':[7,7,9,9,11,13], 'condensation':[[7,9]]},scRef)
    top=inputs[9];topRef=refs[9]['output']
    assert check(top, {'acyclic':True,'order':[4,5,1,0,2,3]},topRef)
    assert not check(top, {'acyclic':True,'order':[2,1,0,3,4,5]},topRef)
    dag=inputs[13];dagRef=refs[13]['output']
    assert check(dag, {'acyclic':True,'distance':[0,1,1,2], 'parent_edge':[-1,4,1,3]},dagRef)
    assert not check(dag, {'acyclic':True,'distance':[0,1,1,2], 'parent_edge':[-1,0,1,0]},dagRef)
    (P/'tests/actual.jsonl').write_text(''.join(json.dumps(a,separators=(',',':'))+'\n' for a in answers[:len(inputs)]))
    report=[f'PASS {len(inputs)} custom cases with independent expected results',
            f'PASS {len(inputs)*2} requests in a single process (state reset)',
            'PASS 4 alternative valid witnesses (MSF, SCC, topo, DAG)',
            'PASS 4 invalid witnesses rejected (MSF, SCC, topo, DAG)']
    (P/'tests/own_test_results.txt').write_text('\n'.join(report)+'\n')
    print('\n'.join(report))
if __name__=='__main__':main()
