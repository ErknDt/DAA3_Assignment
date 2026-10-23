import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Direct (non-JSON task) tests of the required route reconstruction helper. */
public final class RouteHelperTest {
    private static void check(String label, List<Integer> got, List<Integer> expected) {
        if (!got.equals(expected)) throw new AssertionError(label + " expected " + expected + " got " + got);
        System.out.println(label + ": " + got + " PASS");
    }
    private static void checkRouteWeight(int[][] edges, int[] parents, List<Integer> route, long expected) {
        long weight = 0;
        for (int i = 1; i < route.size(); i++) {
            int to = route.get(i), from = route.get(i - 1), id = parents[to];
            if (id < 0 || edges[id][0] != from || edges[id][1] != to)
                throw new AssertionError("predecessor has wrong direction");
            weight += edges[id][2];
        }
        if (weight != expected) throw new AssertionError("weight mismatch " + weight + " vs " + expected);
        System.out.println("route cost: " + weight + " PASS");
    }
    private static void test(String name, int n, int source, int[][] edges, int target,
                             List<Integer> expected, Long expectedWeight) {
        List<List<Integer>> rows = new java.util.ArrayList<>();
        for (int[] e : edges) {
            List<Integer> row = new java.util.ArrayList<>();
            for (int v : e) row.add(v);
            rows.add(row);
        }
        Map<String, Object> input = Map.of("n", n, "source", source, "edges", rows);
        Map<String, Object> result = Algorithms.dag(input);
        if (!Boolean.TRUE.equals(result.get("acyclic"))) throw new AssertionError("cycle not expected");
        int[] parents = (int[]) result.get("parent_edge");
        Long[] distances = (Long[]) result.get("distance");
        List<Integer> path = Algorithms.reconstructRoute(source, target, edges, parents);
        System.out.println(name + " / selected parent_edge IDs: " + Arrays.toString(parents));
        check(name, path, expected);
        if (expectedWeight == null) {
            if (distances[target] != null) throw new AssertionError("target should be unreachable");
        } else {
            if (!expectedWeight.equals(distances[target])) throw new AssertionError("distance mismatch");
            checkRouteWeight(edges, parents, path, expectedWeight);
        }
    }
    public static void main(String[] args) {
        int[][] edges = {{0,1,3},{0,2,8},{1,2,-7},{2,3,4}};
        test("reachable 0 -> 3", 5, 0, edges, 3, List.of(0,1,2,3), 0L);
        test("source 0 -> 0", 5, 0, edges, 0, List.of(0), 0L);
        test("unreachable 0 -> 4", 5, 0, edges, 4, List.of(), null);
        int[][] tied = {{0,1,1},{0,2,1},{1,3,1},{2,3,1},{0,1,1}};
        test("tied/parallel 0 -> 3", 4, 0, tied, 3, List.of(0,2,3), 2L);
        System.out.println("ALL ROUTE HELPER TESTS PASSED");
    }
}
