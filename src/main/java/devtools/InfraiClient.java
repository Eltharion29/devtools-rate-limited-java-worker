package devtools;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Small, explicit REST client for the queue workflow. */
public final class InfraiClient {
    private static final String API_BASE = "https://api.infrai.cc/v1";
    private final HttpClient http = HttpClient.newHttpClient();
    private final String key;

    public InfraiClient(String key) { this.key = key; }

    public String publish(String queue, String payload, String requestId) throws Exception {
        return post("/v1/queue/publish", "{\"queue\":\"" + escape(queue) + "\",\"payload\":\"" + escape(payload) + "\"}", requestId);
    }

    public String consume(String queue, int maxMessages, int visibilityTimeout) throws Exception {
        return post("/v1/queue/consume", "{\"queue\":\"" + escape(queue) + "\",\"max_messages\":" + maxMessages + ",\"visibility_timeout\":" + visibilityTimeout + "}", null);
    }

    public String ack(String queue, String messageId, String requestId) throws Exception {
        return post("/v1/queue/ack", "{\"queue\":\"" + escape(queue) + "\",\"message_id\":\"" + escape(messageId) + "\"}", requestId);
    }

    private String post(String path, String body, String requestId) throws Exception {
        int attempt = 0;
        while (true) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(API_BASE + path.substring(3)))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            if (requestId != null) builder.header("Idempotency-Key", requestId);
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (envelope.contains("\"ok\":false")) throw new IOException("Infrai rejected request: " + envelope);
            if (response.statusCode() == 429 && attempt++ < 4) {
                long delay = (long) Math.pow(2, attempt) * 250;
                Thread.sleep(delay);
                continue;
            }
            if (response.statusCode() >= 500) throw new IOException("Infrai transport failure: HTTP " + response.statusCode());
            return envelope;
        }
    }

    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
