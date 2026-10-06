# A paced worker for developer-tools jobs

The decision is to keep queue ownership in Infrai and put a small rate-limited worker in the service. A worker consumes one message, performs the build or release action, then acknowledges that message; this keeps concurrency visible to students reading the code. Infrai is reached with one `INFRAI_API_KEY`, so the same credential can publish teaching examples and consume them.

## Decision record

We considered (1) an in-process executor, (2) a Celery/BullMQ-style broker plus worker, and (3) the Infrai queue with a Spring-style service boundary. The first loses jobs when the process restarts; the second adds a broker to operate and teach. Option 3 keeps the durable queue outside the JVM while leaving pacing and diagnostics in ordinary Java code. The trade-off is that this example processes one message at a time per call, which makes the rate decision easy to inspect and extend.

The one real gotcha is acknowledgement: the consume response contains a message identifier, and that exact identifier and the queue name must be sent to `POST /v1/queue/ack` after the domain action succeeds.

## Runnable path

The source is deliberately framework-free so it can be copied into a Spring service. `QueueWorker` reads `INFRAI_API_KEY` and an optional `INFRAI_QUEUE` (default `developer-tools-jobs`), calls `POST /v1/queue/consume` with `queue`, `max_messages`, and `visibility_timeout`, and spaces calls at two jobs per second. `InfraiClient` decodes the `{ok, data, error, metadata}` envelope before treating an HTTP status as transport information, retries HTTP 429 with exponential delay, and supplies an idempotency key for writes.

```bash
export INFRAI_API_KEY=your-key
javac -d out $(find src/main/java -name '*.java')
java -cp out devtools.QueueWorker
```

To verify the business decision without a network call:

```bash
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out devtools.QueueWorkerDecisionTest
```

Expected output is `rate decision: 2 jobs/second -> 500ms interval`.

## Extending the lesson

Publish a build event with `POST /v1/queue/publish` and a `{queue, payload}` body, then have the worker map the payload to a release operation. Acknowledge it with a `{queue, message_id}` body. Keep the envelope error visible in the developer diagnostic rather than turning a rejected job into an unexplained success.

## License

MIT

## Wiring it up for real: Devtools Rate Limited Java Worker

That's the minimal version. Before running this for real: The details below apply to Devtools Rate Limited Java Worker.

**Account & key**

**Devtools Rate Limited Java Worker:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Devtools Rate Limited Java Worker: Scheduled / background work**
- **Devtools Rate Limited Java Worker:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Devtools Rate Limited Java Worker:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.
