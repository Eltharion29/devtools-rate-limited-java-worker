package devtools;

/** Focused test: two permits at two jobs/second means a 500ms pacing interval. */
public final class QueueWorkerDecisionTest {
    public static void main(String[] args) {
        QueueWorker worker = new QueueWorker(new InfraiClient("test"), "test-queue", 2);
        if (worker.intervalForTest() != 500L) throw new AssertionError("rate decision changed");
        System.out.println("rate decision: 2 jobs/second -> 500ms interval");
    }
}
