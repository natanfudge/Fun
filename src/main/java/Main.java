import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final List<Runnable> lambdas = new ArrayList<>();

    public static void main(String[] args) {
        subcallAdd(new Pair<>(1, 2));
        while (true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            subcallRun();
        }
    }

    private static void subcallRun() {
        for (Runnable r : lambdas) {
            r.run();
        }
    }

    private static void subcallAdd(Pair<Integer, Integer> x) {
        Runnable lambda = new Runnable() {
            @Override
            public void run() {
                System.out.println(x);
                System.out.println("Halo1");
            }
        };
        lambdas.add(lambda);
    }

    // Simple Pair class
    public static class Pair<A, B> {
        public final A first;
        public final B second;

        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public String toString() {
            return "(" + first + ", " + second + ")";
        }
    }
}
