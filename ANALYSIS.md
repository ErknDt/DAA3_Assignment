# Assignment 3 — Graph Algorithms: Correctness, Complexity, Testing, and Benchmark

**Course:** Design and Analysis of Algorithms  
**Language:** Java (Java 17 source/bytecode target)  
**Project:** Campus festival graph algorithms  
**Date of local testing:** 20 October 2026  
**Interface:** UTF-8 JSON Lines, one independent request per input line, one JSON object per output line.

## 1. How to build, run, and test

The project has **no third-party dependencies**. Install JDK 17 or newer and Python 3.12 or newer. Run commands from the project root:

```bash
python tools/run.py --setup
python tools/run.py --check
python tools/run.py --input tests/my_cases.jsonl
python tests/run_own_tests.py
python tests/randomized_tests.py
python tests/scale_tests.py
```

The offline local `tools/run.py` compiles the selected Java files with `javac --release 17`, then runs the checks; results are written only to stdout as JSON by `Main`. To run the route helper directly and repeat the benchmark (after `--check` has built the sources):

```bash
javac --release 17 -cp .build/Java -d .build/Java tests/RouteHelperTest.java tests/Benchmark.java
java -Xmx384m -cp .build/Java RouteHelperTest
java -Xmx384m -cp .build/Java Benchmark
```

Example direct JSON Lines invocation:

```bash
printf '%s\n' '{"task":"ping"}' '{"task":"dag","n":3,"source":0,"edges":[[0,1,2],[1,2,-3]]}' | java -Xmx384m -cp .build/Java Main
```

On Windows PowerShell, run `python` (or `py -3`) for the Python commands and pipe a UTF-8 input file to `java` for direct invocation. The source directory is `src/Java/`. The `submission.json` file selects `Java`.

**Template note:** This standalone offline copy reconstructs the Java adapter and the publicly described tests because a direct GitHub clone was unavailable in the preparation environment. `tools/public.json` reproduces the 33 short published cases from the source and generates three equivalent deep-chain cases (2,000 vertices); the latter are not claimed to be byte-for-byte copies of the original long fixtures. For the exact original instructor runner, copy `src/Java/Algorithms.java`, `tests/`, `submission.json`, and `ANALYSIS.md` into a repository created using the instructor's GitHub template, then run its unchanged `python tools/run.py --check` and check its GitHub Actions results. The local checks below were **actually executed**, not inferred from the template.

## 2. Global design and invariants

All vertices are numbered `0..n-1`; the original edge ID is the index of an input row in `edges`. **No sorting or adjacency transformation changes this ID.** All mutable algorithm data (DSU arrays, adjacency lists, DFS colors, heap, distance and parent arrays) are allocated inside a particular method invocation; there are no static mutable request-specific fields. This guarantees independence across multiple JSON Lines requests.

Adjacency lists are stored in `head`, `next`, and `to` integer arrays to keep memory linear and avoid allocating a Java object per directed arc. DFS uses an **explicit vertex stack plus one saved adjacency cursor per active vertex**. A suspended DFS therefore resumes at its unprocessed outgoing edge and cannot incur quadratic rescanning or a Java call-stack overflow on 100,000-vertex paths. Integer edge weights fit `int`; total forest costs and DAG path distances use `long`/`Long`.

Invalid or out-of-range inputs are not part of the published assessment contract. JSON output array order is intentionally not assumed to be unique for minimum spanning forests, SCC labels, and topological sorts.

## 3. DSU: disjoint-set union

**Algorithm.** Initially every vertex has itself as parent and size 1. `find` follows parents with path halving (`parent[v] = parent[parent[v]]`). `union(u,v)` first finds representatives, then attaches the smaller tree to the larger tree. It updates the number of connected components and the maximum component size only if the roots differ. After every operation, the algorithm emits `[components, largest]`.

**Invariant.** Each tree contains precisely the vertices of one connected component in the prefix of processed operations. Its root's `size` is the number of vertices in the tree; `components` is the number of roots and `largest` is the largest root size. Initially this holds for singleton sets. Union between different roots merges exactly the corresponding components and updates the count and size; a repeated or self union changes neither. Path compression only changes pointers within the same tree, preserving the represented partition.

**Correctness.** By induction on processed operations, the invariant holds after every union. The two reported statistics therefore equal the required connected-component count and largest component size after each operation.

**Boundary cases.** `n=0` emits no states; for `n>0` and no operations the states list is empty. Self unions and repeated unions are no-ops. Union-by-size handles long sequences of unions.

**Complexity.** `O(n + q α(n))` amortized time and `O(n + q)` memory, counting the emitted states, where `α` is the inverse Ackermann function.

## 4. Kruskal minimum spanning forest

**Algorithm.** Construct a list of the original IDs `0..m-1`, sort by `(weight, edge ID)`, and scan in this order. Accept a candidate only when DSU union of its endpoints succeeds. Sum accepted weights in a `long`; return the original accepted IDs and the DSU component count. A loop is rejected because its endpoints already share a root.

**Invariant.** After considering the first `k` edges, the accepted edges form a forest that can be extended to a minimum spanning forest (MSF). For an edge crossing two current trees, the cut/exchange property permits an optimum MSF containing that lightest eligible edge. An edge with both endpoints in one DSU tree is rejected because it would form a cycle.

**Correctness.** By the safe-edge/exchange argument, every accepted edge preserves extendibility to an MSF. When all edges have been considered, every connected component of the input has been spanned and no cycle was introduced. Thus the forest is minimum-weight in every component, including those with negative edges. The DSU component count includes isolated vertices.

**Boundary cases.** Empty graph, isolates, loops, negative and tied weights, and parallel edges all work. Use original IDs rather than sorted positions. A sum of three `10^9` edges is stored as `3,000,000,000L`, avoiding 32-bit overflow.

**Complexity.** Sorting `m` IDs costs `O(m log(m+1))`, DSU costs `O((n+m)α(n))`, and initialization costs `O(n)`; total `O(n + m log(m+1))` time and `O(n+m)` memory including output.

## 5. Prim minimum spanning forest

**Algorithm.** Independently build an undirected adjacency list with two arcs per original input edge. Iterate over all vertices. For each unvisited root, mark it visited, count a new component, and push its outgoing candidate edges into a min-heap ordered by `(weight, original ID)`. Pop the lightest candidate, ignore it if its destination is already visited, otherwise accept the edge, visit its destination, add its weight, and push its outgoing edges to unvisited vertices. Restart from the next unvisited vertex after the heap empties.

**Invariant.** For each active component, the accepted edges connect exactly the already visited vertices in a tree. Heap candidates are edges discovered from visited vertices; the least-weight candidate whose destination is still unvisited is a minimum-weight edge across the current visited/unvisited cut.

**Correctness.** The cut property makes each newly accepted edge safe for an MSF. A candidate whose destination was previously visited would create a cycle and is skipped. Every vertex is eventually reached from a root or starts its own component, so the output is a spanning forest of every connected component. The roots also count isolated vertices.

**Boundary cases.** Loops lead only to already visited endpoints, so cannot be chosen. Parallel and equal-weight candidates may give another equally valid forest. Negative weights are handled by the same min-heap ordering. A separate root handles each disconnected component, even one without edges.

**Complexity.** Each undirected input edge creates two adjacency arcs and at most one candidate insertion from its first visited endpoint (or fewer for self loops). Heap operations cost `O(log(m+1))`; total `O(n + m log(m+1))` time and `O(n+m)` memory.

## 6. Kosaraju strongly connected components (SCC)

**Algorithm.** Build the original adjacency list and a transposed one. In an *iterative* first DFS of the original graph, push a vertex onto the `finished` array only when all its successors have been examined. Then scan the finish array backward. For every vertex still without a component label, traverse the transposed graph from that vertex and assign a new integer component label to all reached vertices. Finally examine each original edge `(u,v)`; if the labels differ, add the ordered pair `(label[u],label[v])` to `condensation` only once using a `HashSet` of encoded 64-bit pairs.

**Invariant.** The first pass records real DFS finish order, not discovery order, and the second pass always starts with the highest remaining finish time. After each second-pass flood, the assigned vertices constitute one entire SCC and no other SCC: finish-order properties of the condensation DAG prevent the flood from merging another as-yet-unassigned SCC.

**Correctness.** The Kosaraju theorem implies every second-pass DFS discovers precisely one maximal strongly connected set. Thus two vertices receive the same label if and only if each can reach the other in the original graph. The final edge scan adds exactly the cross-SCC arcs; the encoded-pair set eliminates duplicates and all internal arcs are excluded. The condensation graph is therefore exact.

**Boundary cases.** Empty graph -> empty arrays. Isolates -> separate labels. Loops are internal. Duplicate and parallel directed arcs give one condensation pair. Iterative DFS avoids stack overflow on a deep chain. Labels need not be consecutively ordered according to input vertices (this implementation uses consecutive IDs in discovery order).

**Complexity.** `O(n+m)` expected time (expected-constant-time hashing), `O(n+m)` memory including graph, transpose, DFS arrays and condensation.

## 7. DFS topological ordering

**Algorithm.** For every unvisited vertex, run an explicit-stack DFS with colors `0` (unseen), `1` (active), and `2` (finished). Each stack frame's adjacency cursor is saved per vertex. An edge to an active vertex signals a directed cycle immediately. Otherwise, append vertices at DFS completion to the output array **from right to left** (reverse finish order). If any cycle is found, return `acyclic=false` and `order=[]`.

**Invariant.** Active vertices form the current DFS path. Each finished vertex has had all of its outgoing edges examined; in a graph with no back edges, for every edge `(u,v)`, `v` completes before `u` unless it was already completed earlier. An edge to an active vertex forms a back edge to an ancestor on the current path, hence a cycle.

**Correctness.** Any cycle in a fully explored directed graph produces a back edge during DFS; conversely a back edge constructs a directed cycle. If none appears, reversing the finishing order puts every edge's origin before its destination, so the resulting permutation is a valid topological order. The outer loop ensures even disconnected components are checked.

**Boundary cases.** Empty graph -> `true,[]`. Isolates appear once in the order. A self loop makes `acyclic=false`. Parallel edges cause no problem. A cycle unreachable from other roots is still detected.

**Complexity.** `O(n+m)` time (each arc cursor advances once) and `O(n+m)` memory, including graph and output.

## 8. DAG shortest paths and route reconstruction

**Algorithm.** First call the same whole-graph three-color DFS topological routine. A cycle anywhere makes the result `acyclic=false`, `distance=[]`, and `parent_edge=[]`. If acyclic, initialize a nullable `Long[] distance` (`null` means unreachable) and a `parentEdge[]` filled with `-1`; set the source distance to `0L`. Process every vertex in topological order. For reachable `u` only, relax each outgoing edge with candidate `distance[u] + weight`; update `distance[v]` and store the edge's **original index** in `parentEdge[v]` only for a strictly better candidate.

**Invariant.** Immediately before a vertex `u` is processed, its distance equals the shortest path length from `source` to `u` (or is unreachable), because all incoming edges to `u` originate at earlier vertices in a topological order. Any improving assignment records a real edge out of a reachable vertex, so the chosen parent belongs to a real source-to-vertex path.

**Correctness.** Every directed path entering `u` ends in an edge from a predecessor, processed earlier. By induction over topological order, all predecessor distances are final, so relaxing every incoming edge yields the minimum cost for `u`. Negative weights do not invalidate this argument, since a DAG has no directed cycles. The `parentEdge` witness ends at the named vertex, has exactly the correct cumulative cost, and forms an acyclic chain back to the source. The source has distance zero and parent `-1`; unreachable vertices retain `null` and `-1`.

**Reconstruction helper.** `Algorithms.reconstructRoute(source,target,edges,parentEdge)` starts at `target` and follows each stored input edge ID backwards: it verifies that the referenced edge ends at the current vertex, moves to the edge's source, and stops at `source`; then it reverses the vertex list. It returns `[]` for an unreachable/invalid chain and `[source]` for the source itself. A defensive length bound prevents infinite traversal if given malformed parent pointers. Every step is validated against original edge direction.

**Actual required helper calls** (`java -cp .build/Java RouteHelperTest`; full output in `tests/route_results.txt`):

| Case | Input source / target | Chosen `parent_edge` | Reconstructed vertices | Verified cost |
| --- | --- | --- | --- | ---: |
| Reachable | 0 -> 3 | `[-1,0,2,3,-1]` | `[0,1,2,3]` | 0 |
| Source | 0 -> 0 | `[-1,0,2,3,-1]` | `[0]` | 0 |
| Unreachable | 0 -> 4 | `[-1,0,2,3,-1]` | `[]` | N/A (`distance[4] = null`) |
| Tied + parallel edges | 0 -> 3 | `[-1,4,1,3]` | `[0,2,3]` | 2 |

For the last case, the chosen parallel-edge ID `4` is an equally optimal predecessor for vertex `1`, while the route to vertex `3` uses edge IDs `1,3`; the alternative valid route `0,1,3` also costs 2. Verification sums edge weights along the reconstructed route and checks `(from,to)` direction at every step.

**Boundary cases.** Negative, zero and 64-bit distances work; a distance of `-1` is not confused with unreachable `null`. Tied paths and parallel input edges may give different valid original IDs. A self loop or a cycle even outside the reachable area yields `acyclic=false` and empty output arrays. `dag` requires `n>=1` and a valid source.

**Complexity.** `O(n+m)` time and memory for topological detection and relaxation; reconstruction is `O(L)` time and output memory for a path of `L` vertices (for valid produced witnesses).

## 9. Testing and observed results

### 9.1 Published-contract / offline runner

Commands actually executed:

```bash
python tools/run.py --setup
python tools/run.py --check
```

**Observed:** ping setup passed; **36/36** locally reconstructed public-contract cases passed; the extra **two complete batches in one process** state-reset test passed. This offline suite mirrors the short published checks and uses generated 2,000-vertex chains for the last three cases. It is **not** a claim of execution on GitHub Actions or the instructor's untouched downloaded suite.

### 9.2 Independently justified own tests

`tests/my_cases.jsonl` has **17** input requests, with independent, human-readable explanations and expected responses in `tests/expected.json`. `python tests/run_own_tests.py` generated `tests/actual.jsonl` and the result log. Observed: **17/17** correct, **34** back-to-back mixed requests correct, **4** valid alternative witnesses accepted and **4** invalid witnesses rejected. Specifically:

- Minimum spanning forest: a different order of IDs for the same valid optimal forest is accepted; repeated edge ID is rejected.
- SCC: renaming labels bijectively is accepted; omitting a condensation arc is rejected.
- Topological ordering: another precedence-respecting permutation is accepted; a precedence violation is rejected.
- DAG paths: choosing tied or parallel optimal edges is accepted; a parent ID not ending at its vertex is rejected.

For a broader independent oracle, `python tests/randomized_tests.py` uses seed **31415926** for 300 random small cases: 50 each DSU, Kruskal, Prim, SCC, topo, and DAG. It compares against independent components logic, exhaustive minimum-forest subset enumeration, Floyd–Warshall reachability, Kahn's cycle detection, and Bellman–Ford relaxation. **300/300 passed** in the actual local run; see `tests/randomized_results.txt`.

### 9.3 Full published bounds and deep chains

`python tests/scale_tests.py` creates and checks six maximum-size cases with `n=100,000` and `q=250,000` or `m=250,000`, with the Java process limited to `-Xmx384m` and a 20-second timeout per case. Results (actual full-process elapsed times from `tests/scale_results.txt`):

| Task | n | Operations/edges | Result | Seconds |
| --- | ---: | ---: | --- | ---: |
| DSU | 100,000 | 250,000 | PASS | 0.623 |
| Kruskal | 100,000 | 250,000 | PASS | 0.355 |
| Prim | 100,000 | 250,000 | PASS | 0.398 |
| SCC | 100,000 | 250,000 | PASS | 0.659 |
| Topological DFS | 100,000 | 250,000 | PASS | 0.436 |
| DAG shortest paths | 100,000 | 250,000 | PASS | 0.535 |

These use a backbone chain and repeated adjacent edges to keep expected answers independently checkable. The SCC, topological, and DAG cases exercise depth 100,000 without recursion. These timings include a fresh Java process plus JSON I/O; **they are separate from the Kruskal–Prim comparison benchmark below** and are not intended as a representative speed comparison.

### 9.4 Additional boundary considerations

The above tests cover zero vertices where allowed, isolates, duplicate and self unions, undirected and directed self loops, parallel and tied edges, negative weights, sum/distance `3,000,000,000`, unreachable vertices, a cycle unreachable from the DAG source, singleton graphs, deep chains, and multiple mixed requests in one process. The Java adapter's output is one JSON object per line with no debugging messages on standard output.

## 10. Kruskal vs Prim benchmark on identical graphs

### 10.1 Reproducible setup and measurement protocol

- **Generator:** `tests/Benchmark.java`, function `generate(n,m)`, Java `Random` with base seed `20261020` and per-size seed `20261020 + 31*n + m`.
- **Graph:** undirected, connected via a backbone of `n-1` edges `(i,i+1)`; remaining edges have independent random endpoints, so parallels/loops are possible. All weights are integers sampled uniformly from `[-1000,1000]`. Both algorithms process the same `int[][]` and the same request map.
- **Sizes:** `(n,m)=(1,000,4,000)`, `(5,000,20,000)`, and `(10,000,40,000)`.
- **Validity precondition:** For every graph, before any timing, both results are checked to have the same optimal cost and component count; a separate forest witness verifier checks all selected IDs, no repetitions/cycles, selected-weight sum, and that the selected forest spans the input graph.
- **Timer:** `System.nanoTime()`, duration in nanoseconds, shown below in milliseconds. Five timed runs per algorithm per size (more than the three required); each median is the third sorted observation.
- **Warm-up:** three untimed calls of **both** algorithms at each size, plus pre-timing correctness calls. After warm-up, the invocation order is alternated each iteration to mitigate order bias.
- **Timing boundary:** measured **inside one JVM**, around a single call to `Algorithms.kruskal(input)` or `Algorithms.prim(input)`. Includes each method's own conversion from the parsed input lists to `int[][]`, sorting/adjacency construction, DSU/heap work, list/result allocation; excludes Java compilation, input JSON parsing, graph generation, result JSON serialization, correctness validation, external process launch, and warm-up. Both methods use the **same boundary**.
- **Runtime:** `java -Xmx384m -cp .build/Java Benchmark`, JDK OpenJDK `21.0.12.1`, compiling Java with `javac --release 17`. Python `3.13.5` is used only for auxiliary checks.
- **Environment:** Linux x86_64, kernel `6.18.55`, AMD EPYC 9V74 80-Core Processor (5 virtual CPUs visible to the container), KVM virtualized, approx. 5.81 GiB memory visible at measurement time. Results recorded 20 October 2026 UTC. Virtualization/JIT/GC effects can make timings variable.
- **Repeat:** after build and test-class compilation, run `java -Xmx384m -cp .build/Java Benchmark`. Raw results are retained in `tests/benchmark_results.csv`.

### 10.2 Actual measured results

Time values below are **milliseconds**, not predicted or fabricated. Each row corresponds to the **same graph** at that size.

| n | m | Algorithm | Run 1 | Run 2 | Run 3 | Run 4 | Run 5 | Median | Optimum cost |
| ---: | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1,000 | 4,000 | Kruskal | 4.884 | 3.242 | 1.666 | 3.379 | 2.489 | **3.242** | -705,627 |
| 1,000 | 4,000 | Prim | 3.768 | 4.256 | 1.636 | 5.885 | 2.087 | **3.768** | -705,627 |
| 5,000 | 20,000 | Kruskal | 23.107 | 15.972 | 13.784 | 16.589 | 11.965 | **15.972** | -3,525,595 |
| 5,000 | 20,000 | Prim | 12.894 | 12.804 | 9.471 | 9.744 | 10.017 | **10.017** | -3,525,595 |
| 10,000 | 40,000 | Kruskal | 14.147 | 17.097 | 16.718 | 13.302 | 14.483 | **14.483** | -7,036,431 |
| 10,000 | 40,000 | Prim | 30.947 | 24.569 | 26.216 | 19.785 | 19.488 | **24.569** | -7,036,431 |

All six method/size results have **one connected component**, and their edge selections were validated before timing. Exact nanosecond observations (not rounded) remain in `tests/benchmark_results.csv`.

### 10.3 Interpretation and limitations

These observations **do not establish that one algorithm is universally faster**. Kruskal had a lower median at 1,000 and 10,000 vertices; Prim had a lower median at 5,000 vertices. Short managed-runtime measurements are sensitive to JVM JIT compilation, allocation, garbage collection, CPU scheduling, cache state, and the container's shared/virtualized hardware. The unexpectedly lower Kruskal median at 10,000 than at 5,000 illustrates this noise. A stronger performance study would increase repetitions, run separate process forks, control CPU affinity/GC, and compare multiple graph densities and degree distributions.

Asymptotically, this Kruskal implementation sorts `m` edges and is `O(n+m log(m+1))`. This Prim implementation uses a lazy priority queue with `O(n+m log(m+1))` time. Input structure, data structures, and constant factors—not just asymptotics—determine the observed measurements at these sizes.

## 11. Sources, assistance, and verification

1. Instructor's Assignment 3 PDF (`docs/Assignment_3_EN.pdf`) and public GitHub repository: <https://github.com/RZhaniya/daa-assignment-3-graphs>. Task contracts, complexity targets, supported languages, and the public-check conventions are from these sources.
2. Classical definitions and proofs of DSU, Kruskal's cut property, Prim's cut property, Kosaraju's finish-time ordering, DFS topological ordering, and DAG relaxation as studied in Design and Analysis of Algorithms.
3. **AI assistance disclosure:** ChatGPT was used to help with parts of the test execution, debugging, performance benchmarking, and preparation of Markdown documentation (README.md and ANALYSIS.md). AI assistance also supported code review and explanations of algorithm correctness and complexity.
