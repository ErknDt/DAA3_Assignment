import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Assignment 3: six graph algorithms. Each call owns all its mutable state. */
public final class Algorithms {
    private Algorithms() {}

    /** Union by size with path compression. */
    private static final class DSU {
        final int[] parent;
        final int[] size;
        int components;
        int largest;

        DSU(int n) {
            parent = new int[n];
            size = new int[n];
            components = n;
            largest = n == 0 ? 0 : 1;
            for (int i = 0; i < n; i++) {
                parent[i] = i;
                size[i] = 1;
            }
        }

        int find(int vertex) {
            while (parent[vertex] != vertex) {
                parent[vertex] = parent[parent[vertex]];
                vertex = parent[vertex];
            }
            return vertex;
        }

        boolean union(int u, int v) {
            int a = find(u), b = find(v);
            if (a == b) return false;
            if (size[a] < size[b]) {
                int temp = a;
                a = b;
                b = temp;
            }
            parent[b] = a;
            size[a] += size[b];
            components--;
            largest = Math.max(largest, size[a]);
            return true;
        }
    }

    static Map<String, Object> dsu(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] operations = Input.operations(input);
        DSU groups = new DSU(n);
        int[][] states = new int[operations.length][2];
        for (int i = 0; i < operations.length; i++) {
            groups.union(operations[i][0], operations[i][1]);
            states[i][0] = groups.components;
            states[i][1] = groups.largest;
        }
        return Map.of("states", states);
    }

    /** Kruskal: sort original edge IDs and accept only inter-component edges. */
    static Map<String, Object> kruskal(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);
        Integer[] order = new Integer[edges.length];
        for (int i = 0; i < edges.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.<Integer>comparingInt(id -> edges[id][2])
                .thenComparingInt(id -> id));
        DSU groups = new DSU(n);
        List<Integer> selected = new ArrayList<>();
        long cost = 0L;
        for (int id : order) {
            int[] edge = edges[id];
            if (groups.union(edge[0], edge[1])) {
                cost += (long) edge[2];
                selected.add(id);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cost", cost);
        result.put("edge_ids", selected);
        result.put("components", groups.components);
        return result;
    }

    private static final class Candidate {
        final int destination, edgeId, weight;
        Candidate(int destination, int edgeId, int weight) {
            this.destination = destination;
            this.edgeId = edgeId;
            this.weight = weight;
        }
    }

    /** Prim: independent lazy-heap algorithm, restarted at each isolate/component. */
    static Map<String, Object> prim(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);
        int[] head = new int[n];
        Arrays.fill(head, -1);
        int[] target = new int[2 * edges.length];
        int[] next = new int[2 * edges.length];
        int[] id = new int[2 * edges.length];
        int at = 0;
        for (int i = 0; i < edges.length; i++) {
            int u = edges[i][0], v = edges[i][1];
            target[at] = v;
            id[at] = i;
            next[at] = head[u];
            head[u] = at++;
            target[at] = u;
            id[at] = i;
            next[at] = head[v];
            head[v] = at++;
        }
        boolean[] visited = new boolean[n];
        PriorityQueue<Candidate> heap = new PriorityQueue<>(
                Comparator.comparingInt((Candidate item) -> item.weight)
                        .thenComparingInt(item -> item.edgeId));
        List<Integer> selected = new ArrayList<>();
        int components = 0;
        long cost = 0L;
        for (int root = 0; root < n; root++) {
            if (visited[root]) continue;
            components++;
            visited[root] = true;
            for (int arc = head[root]; arc != -1; arc = next[arc]) {
                int edgeId = id[arc];
                if (!visited[target[arc]])
                    heap.add(new Candidate(target[arc], edgeId, edges[edgeId][2]));
            }
            while (!heap.isEmpty()) {
                Candidate candidate = heap.poll();
                int v = candidate.destination;
                if (visited[v]) continue;
                visited[v] = true;
                cost += (long) candidate.weight;
                selected.add(candidate.edgeId);
                for (int arc = head[v]; arc != -1; arc = next[arc]) {
                    int edgeId = id[arc];
                    if (!visited[target[arc]])
                        heap.add(new Candidate(target[arc], edgeId, edges[edgeId][2]));
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cost", cost);
        result.put("edge_ids", selected);
        result.put("components", components);
        return result;
    }

    /** Two-pass, fully iterative Kosaraju; no recursive-stack depth limit. */
    static Map<String, Object> scc(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);
        int m = edges.length;
        int[] head = new int[n], reverseHead = new int[n];
        Arrays.fill(head, -1);
        Arrays.fill(reverseHead, -1);
        int[] to = new int[m], next = new int[m], reverseTo = new int[m], reverseNext = new int[m];
        for (int i = 0; i < m; i++) {
            int u = edges[i][0], v = edges[i][1];
            to[i] = v;
            next[i] = head[u];
            head[u] = i;
            reverseTo[i] = u;
            reverseNext[i] = reverseHead[v];
            reverseHead[v] = i;
        }
        byte[] state = new byte[n];
        int[] cursor = new int[n], stack = new int[n], finished = new int[n];
        int finishCount = 0;
        for (int root = 0; root < n; root++) {
            if (state[root] != 0) continue;
            int sp = 0;
            stack[sp++] = root;
            state[root] = 1;
            cursor[root] = head[root];
            while (sp > 0) {
                int u = stack[sp - 1];
                int arc = cursor[u];
                if (arc == -1) {
                    sp--;
                    state[u] = 2;
                    finished[finishCount++] = u;
                } else {
                    cursor[u] = next[arc];
                    int v = to[arc];
                    if (state[v] == 0) {
                        state[v] = 1;
                        cursor[v] = head[v];
                        stack[sp++] = v;
                    }
                }
            }
        }
        int[] component = new int[n];
        Arrays.fill(component, -1);
        int count = 0;
        for (int k = finishCount - 1; k >= 0; k--) {
            int root = finished[k];
            if (component[root] != -1) continue;
            int sp = 0;
            stack[sp++] = root;
            component[root] = count;
            while (sp > 0) {
                int u = stack[--sp];
                for (int arc = reverseHead[u]; arc != -1; arc = reverseNext[arc]) {
                    int v = reverseTo[arc];
                    if (component[v] == -1) {
                        component[v] = count;
                        stack[sp++] = v;
                    }
                }
            }
            count++;
        }
        Set<Long> seen = new HashSet<>();
        List<int[]> condensation = new ArrayList<>();
        for (int[] edge : edges) {
            int a = component[edge[0]], b = component[edge[1]];
            if (a == b) continue;
            long pair = ((long) a << 32) | (b & 0xffffffffL);
            if (seen.add(pair)) condensation.add(new int[]{a, b});
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("component", component);
        result.put("condensation", condensation);
        return result;
    }

    /** Null denotes a directed cycle (including cycles in disconnected portions). */
    private static int[] dfsTopologicalOrder(int n, int[][] edges) {
        int[] head = new int[n];
        Arrays.fill(head, -1);
        int[] next = new int[edges.length];
        for (int i = 0; i < edges.length; i++) {
            int u = edges[i][0];
            next[i] = head[u];
            head[u] = i;
        }
        byte[] state = new byte[n];
        int[] cursor = new int[n], stack = new int[n], order = new int[n];
        int finish = n;
        for (int root = 0; root < n; root++) {
            if (state[root] != 0) continue;
            int sp = 0;
            stack[sp++] = root;
            state[root] = 1;
            cursor[root] = head[root];
            while (sp > 0) {
                int u = stack[sp - 1];
                int arc = cursor[u];
                if (arc == -1) {
                    sp--;
                    state[u] = 2;
                    order[--finish] = u;
                } else {
                    cursor[u] = next[arc];
                    int v = edges[arc][1];
                    if (state[v] == 1) return null;
                    if (state[v] == 0) {
                        state[v] = 1;
                        cursor[v] = head[v];
                        stack[sp++] = v;
                    }
                }
            }
        }
        return order;
    }

    static Map<String, Object> topo(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);
        int[] order = dfsTopologicalOrder(n, edges);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("acyclic", order != null);
        result.put("order", order == null ? new int[0] : order);
        return result;
    }

    static Map<String, Object> dag(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int source = Input.source(input);
        int[][] edges = Input.edges(input);
        int[] order = dfsTopologicalOrder(n, edges);
        Map<String, Object> result = new LinkedHashMap<>();
        if (order == null) {
            result.put("acyclic", false);
            result.put("distance", new Object[0]);
            result.put("parent_edge", new int[0]);
            return result;
        }
        int[] head = new int[n];
        Arrays.fill(head, -1);
        int[] next = new int[edges.length];
        for (int i = 0; i < edges.length; i++) {
            int u = edges[i][0];
            next[i] = head[u];
            head[u] = i;
        }
        Long[] distance = new Long[n]; // null = unreachable, including valid negative distance -1
        int[] parentEdge = new int[n];
        Arrays.fill(parentEdge, -1);
        distance[source] = 0L;
        for (int u : order) {
            if (distance[u] == null) continue;
            for (int arc = head[u]; arc != -1; arc = next[arc]) {
                int[] edge = edges[arc];
                int v = edge[1];
                long candidate = distance[u] + (long) edge[2];
                if (distance[v] == null || candidate < distance[v]) {
                    distance[v] = candidate;
                    parentEdge[v] = arc; // original edge ID
                }
            }
        }
        result.put("acyclic", true);
        result.put("distance", distance);
        result.put("parent_edge", parentEdge);
        return result;
    }

    /**
     * Reconstructs source -> target vertex sequence using ORIGINAL input edge IDs.
     * Returns an empty list for unreachable/invalid predecessor chains, [source] for source.
     * Time O(L) for a valid route of L vertices, memory O(L).
     */
    static List<Integer> reconstructRoute(int source, int target, int[][] edges, int[] parentEdge) {
        List<Integer> backwards = new ArrayList<>();
        int current = target;
        for (int steps = 0; steps <= parentEdge.length; steps++) {
            backwards.add(current);
            if (current == source) {
                java.util.Collections.reverse(backwards);
                return backwards;
            }
            if (current < 0 || current >= parentEdge.length) return List.of();
            int edgeId = parentEdge[current];
            if (edgeId < 0 || edgeId >= edges.length || edges[edgeId][1] != current)
                return List.of();
            current = edges[edgeId][0];
        }
        return List.of();
    }
}
