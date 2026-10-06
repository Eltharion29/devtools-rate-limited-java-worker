package devtools;

import java.util.concurrent.Semaphore;

/** Rate-limited consumer for build and release jobs. */
public final class QueueWorker {
    private final InfraiClient client;
    private final String queue;
    private final Semaphore permits;
    private final long intervalMillis;

    public QueueWorker(InfraiClient client, String queue, int perSecond) {
        this.client = client;
        this.queue = queue;
        this.permits = new Semaphore(perSecond);
        this.intervalMillis = 1000L / perSecond;
    }

    public String processOne() throws Exception {
        permits.acquire();
        try {
            String envelope = client.consume(queue, 1, 60);
            Thread.sleep(intervalMillis);
            return envelope;
        } finally { permits.release(); }
    }

    long intervalForTest() { return intervalMillis; }

    public static void main(String[] args) throws Exception {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY");
        String queue = System.getenv().getOrDefault("INFRAI_QUEUE", "developer-tools-jobs");
        QueueWorker worker = new QueueWorker(new InfraiClient(key), queue, 2);
        System.out.println(worker.processOne());
    }
}
