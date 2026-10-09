# Mini Load Tester

Mini Load Tester is a Java 17 command-line project for learning and demonstrating the mechanics behind HTTP load generation. It supports both closed-loop (concurrency-based) and open-loop (fixed-arrival-rate) tests, with no third-party runtime dependencies.

This is a local, portfolio-focused project: it is intended to be built, read, tested, and run from source, not deployed as a service.

## Project goals

- Keep the application runtime on the Java standard library (`java.net.http` and the JDK HTTP server).
- Make the differences between closed-loop and open-loop traffic explicit.
- Bound in-flight work and report open-loop arrivals that cannot be dispatched.
- Keep the engine, HTTP client, metrics, reporting, and CLI in separate packages.
- Use tests to document behavior and protect the load and measurement models.

Maven is used to compile and package the executable JAR; JUnit is test-scope only and is not bundled as a runtime dependency.

## Build and run

Requirements: JDK 17+ and Maven.

```bash
mvn test
mvn package
java -jar target/mini-load-tester.jar --url http://localhost:8080/ -c 50 -d 30s
```

The helper script rebuilds the project before each run, then forwards its arguments:

```bash
./run.sh --url http://localhost:8080/ -c 50 -d 30s
```

## Load models

### Closed-loop

```bash
java -jar target/mini-load-tester.jar --url http://localhost:8080/ -c 50 -d 30s
```

Each virtual user sends a request, waits for its response, then sends another request. Use this mode to ask how a service behaves with a fixed number of users. Throughput naturally falls as response times rise.

### Open-loop

```bash
java -jar target/mini-load-tester.jar --url http://localhost:8080/ -r 200 -c 500 -d 2m
```

Requests are scheduled at the target rate independently of earlier response times. `--concurrency` is the maximum number of requests allowed in flight. When that cap is full, the scheduled arrival is skipped rather than queued; the final report and exported files include the dropped-arrival count. Increase the cap or lower the rate if arrivals are being dropped.

Open-loop latency is measured from the scheduled arrival time through completion, so scheduler dispatch delay is not hidden. Closed-loop traffic does not provide this fixed-arrival-rate view and can under-represent latency during slowdowns (coordinated omission).

## Options

```text
--url URL                  Target HTTP or HTTPS URL (required)
--method METHOD            GET, POST, PUT, DELETE, PATCH, or HEAD (default: GET)
--concurrency, -c N        Virtual users, or open-loop max in-flight (default: 10)
--rate, -r N               Target requests per second; enables open-loop mode
--duration, -d DURATION    Measurement duration, e.g. 30s, 5m, 1h (default: 30s)
--warmup DURATION          Warmup traffic excluded from reported metrics (default: 0s)
--ramp-up SPEC             constant, linear, step, or spike (default: constant)
--timeout DURATION         Per-request timeout (default: 10s)
--think-time DURATION      Delay between closed-loop requests per user (default: 0s)
--header "Key: Value"      Add a request header; repeatable
--body TEXT                UTF-8 request body
--body-file PATH           Read request body from a file; mutually exclusive with --body
--content-type TYPE        Shortcut for the Content-Type header
--output PATH              Export results as .json or .csv
--help, -h                 Show usage
```

Ramp-up uses the selected model's load unit (users in closed-loop, requests/second in open-loop):

```text
constant
linear:<startValue>:<rampSeconds>
step:<numSteps>:<stepSeconds>
spike:<baselineValue>:<atSecond>:<spikeDurationSeconds>
```

Example: ramp from 0 to 300 requests/second over 30 seconds, then hold:

```bash
java -jar target/mini-load-tester.jar \
  --url http://localhost:8080/ -r 300 -c 1000 -d 3m --ramp-up linear:0:30
```

## Local demo target

The included JDK HTTP server simulates latency, jitter, errors, and a concurrency limit. Build first, then start it in one terminal:

```bash
java -cp target/classes com.loadtester.testserver.DemoTargetServer \
  --port 8080 --latency-ms 30 --jitter-ms 15 --error-rate 0.01
```

Run either load model against `http://localhost:8080/` from another terminal.

## Results

The console and optional JSON/CSV export report completed request counts, errors, throughput, status codes, latency percentiles, scheduler delays, and dropped open-loop arrivals. Warmup results are excluded. Measured throughput uses the configured measurement window; requests started inside that window are allowed to finish before the final report is produced.

The latency histogram is a bounded-memory approximation, not a replacement for a production-grade benchmarking suite. Use a controlled environment and compare runs carefully; network, JVM warmup, host scheduling, and the load generator itself affect results.

## Layout

```text
src/main/java/com/loadtester/
  Main.java          CLI entry point
  cli/               argument parsing and usage
  config/            immutable run configuration
  engine/            open-/closed-loop runners and ramp-up
  http/              JDK HTTP request execution
  metrics/           bounded-memory latency and result aggregation
  report/            console output and JSON/CSV export
  testserver/        local demo HTTP target
  util/              duration parsing, formatting, and thread factories
src/test/java/        unit and behavior tests
```

Run the test suite with:

```bash
mvn test
```

## Screenshot

![Demo](screenshot.png)
