# Mini Load Tester

A Java 17 CLI for exploring HTTP load testing. It supports closed-loop and open-loop traffic and has no third-party runtime dependencies. Built as a local, portfolio-focused project—not a hosted service.


## Load models

**Closed-loop** (`-c`): each virtual user waits for a response before sending another request.

```bash
-c 50 -d 30s
```

**Open-loop** (`-r`): schedules requests at a target rate. `-c` sets the maximum in-flight requests. Arrivals are skipped—not queued—when the cap is full, and the report shows the drop count.

```bash
-r 200 -c 500 -d 2m
```

## Options

| Option | Description |
| --- | --- |
| `--url URL` | Target HTTP(S) URL (required) |
| `--method METHOD` | GET, POST, PUT, DELETE, PATCH, or HEAD |
| `-c, --concurrency N` | Virtual users or max in-flight requests |
| `-r, --rate N` | Target requests per second; enables open-loop mode |
| `-d, --duration TIME` | Measurement duration, e.g. `30s` or `2m` |
| `--warmup TIME` | Warmup duration, excluded from results |
| `--ramp-up SPEC` | `constant`, `linear`, `step`, or `spike` |
| `--timeout TIME` | Per-request timeout |
| `--think-time TIME` | Delay between closed-loop requests |
| `--header "Key: Value"` | Add a request header (repeatable) |
| `--body TEXT` / `--body-file PATH` | Set request body |
| `--content-type TYPE` | Set the Content-Type header |
| `--output PATH` | Export results as `.json` or `.csv` |

Use `--help` for defaults and ramp-up syntax. Results include request counts, errors, throughput, status codes, and latency percentiles.

## Build and run

Requires JDK 17+ and Maven.

```bash
mvn test
mvn package
```

### Built-in demo server

Start the included server in one terminal:

```bash
java -cp target/classes com.loadtester.testserver.DemoTargetServer \
  --port 8080 --latency-ms 30 --jitter-ms 15 --error-rate 0.01
```

In another terminal, run a test against it:

```bash
java -jar target/mini-load-tester.jar --url http://localhost:8080/ -c 20 -d 30s
```

### Your own API endpoint

Use your endpoint URL in `--url`. For example, test a health endpoint:

```bash
java -jar target/mini-load-tester.jar --url https://api.example.com/health -r 50 -c 100 -d 1m
```

For an API that requires a JSON request body, provide a body file and headers:

```bash
java -jar target/mini-load-tester.jar --url https://api.example.com/orders \
  --method POST --content-type application/json \
  --body-file examples/sample-body.json -c 10 -d 30s
```

![Demo](screenshot.png)
