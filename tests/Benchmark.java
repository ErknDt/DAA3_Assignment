import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Reproducible in-process timed calls of the required Algorithms methods. */
public final class Benchmark {
    private static final long SEED = 20261020L;
    private static final int WARMUPS = 3;
    private static final int RUNS = 5;
    private static final int[][] SIZES = {{1000,4000},{5000,20000},{10000,40000}};
    private static final class DSU {
        final int[] p, s;
        int cc;
        DSU(int n) { p = new int[n];s = new int[n];cc=n; for (int i=0;i<n;i++){p[i]=i;s[i]=1;} }
        int find(int x) {while (x!=p[x]){p[x]=p[p[x]]; x=p[x];}return x;}
        boolean join(int a,int b) {a=find(a);b=find(b);if(a==b)return false;if(s[a]<s[b]){int t=a;a=b;b=t;}p[b]=a;s[a]+=s[b];cc--;return true;}
    }
    private static int[][] generate(int n, int m) {
        // Force a connected base tree; the rest include parallel edges, loops, and ties.
        Random rng = new Random(SEED + 31L * n + m);
        int[][] edges = new int[m][3];
        for (int i=0; i<n-1; i++) {
            edges[i][0]=i; edges[i][1]=i+1; edges[i][2]=rng.nextInt(2001)-1000;
        }
        for (int i=n-1; i<m; i++) {
            edges[i][0]=rng.nextInt(n); edges[i][1]=rng.nextInt(n);
            edges[i][2]=rng.nextInt(2001)-1000;
        }
        return edges;
    }
    private static void verify(Map<String,Object> output, int[][] edges, int n) {
        int components = ((Number) output.get("components")).intValue();
        @SuppressWarnings("unchecked") List<Integer> ids = (List<Integer>) output.get("edge_ids");
        long cost = ((Number) output.get("cost")).longValue();
        if (ids.size() != n-components) throw new AssertionError("forest edge count");
        DSU dsu = new DSU(n);
        boolean[] used = new boolean[edges.length];
        long recomputed = 0;
        for (int id: ids) {
            if (used[id] || !dsu.join(edges[id][0],edges[id][1])) throw new AssertionError("cyclic forest");
            used[id]=true;recomputed += edges[id][2];
        }
        if (dsu.cc != components || recomputed != cost) throw new AssertionError("cost/components");
        for (int[] edge : edges) if (dsu.find(edge[0]) != dsu.find(edge[1]))
            throw new AssertionError("not spanning");
    }
    private static long median(long[] data) {
        long[] sorted = data.clone();Arrays.sort(sorted);return sorted[sorted.length/2];
    }
    public static void main(String[] args) {
        System.out.println("n,m,algorithm,run1_ns,run2_ns,run3_ns,run4_ns,run5_ns,median_ns,cost,components");
        for (int[] size:SIZES) {
            int n=size[0],m=size[1];
            int[][] edges=generate(n,m);
            List<List<Integer>> rows=new ArrayList<>(m);
            for (int[] e:edges) rows.add(Arrays.asList(e[0],e[1],e[2]));
            Map<String,Object> input=Map.of("n",n,"edges",rows);
            Map<String,Object> kruskal=Algorithms.kruskal(input),prim=Algorithms.prim(input);
            verify(kruskal,edges,n);verify(prim,edges,n);
            if (!kruskal.get("cost").equals(prim.get("cost"))
                    || !kruskal.get("components").equals(prim.get("components")))
                throw new AssertionError("Different optimum");
            // Warm up BOTH methods on the SAME input before timing either.
            for (int w=0;w<WARMUPS;w++) {
                Algorithms.kruskal(input); Algorithms.prim(input);
            }
            long[] kTimes=new long[RUNS],pTimes=new long[RUNS];
            for (int r=0;r<RUNS;r++) {
                if ((r&1)==0) {
                    long now=System.nanoTime();Algorithms.kruskal(input);kTimes[r]=System.nanoTime()-now;
                    now=System.nanoTime();Algorithms.prim(input);pTimes[r]=System.nanoTime()-now;
                } else {
                    long now=System.nanoTime();Algorithms.prim(input);pTimes[r]=System.nanoTime()-now;
                    now=System.nanoTime();Algorithms.kruskal(input);kTimes[r]=System.nanoTime()-now;
                }
            }
            for (String name:List.of("Kruskal","Prim")) {
                long[] sample=name.equals("Kruskal")?kTimes:pTimes;
                StringBuilder line=new StringBuilder(n+","+m+","+name);
                for (long ns:sample)line.append(',').append(ns);
                line.append(',').append(median(sample)).append(',')
                    .append(kruskal.get("cost")).append(',').append(kruskal.get("components"));
                System.out.println(line);
            }
        }
    }
}
