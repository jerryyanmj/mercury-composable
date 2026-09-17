# Mortgage Amortization Calculator

A minimal, standalone composable application whose **calculation lives in a MiniGraph model**
(`graph.math`), with two small Java helper functions only for what the engine can't do
(building the per-month extras array, and calendar dates + row assembly).

## Run
```bash
mvn clean package
java -jar target/mortgage-calc-4.11.5.jar        # serves on http://127.0.0.1:8083
```

## Call
```bash
curl -X POST http://127.0.0.1:8083/api/graph/mortgage-calc \
  -H "Content-Type: application/json" \
  -d '{"loan_amount":200000,"annual_rate":6.0,"term_years":30,"start_date":"2026-01-01","extra_monthly":200}'
```
Required: `loan_amount`, `annual_rate` (percent), `term_years`, `start_date` (yyyy-MM-dd).
Optional: `extra_monthly`, `extra_annual`, `lump_sums:[{month,amount}]` (month > 1),
`annual_property_tax`, `annual_home_insurance`.

Returns `{summary, schedule}` — monthly P&I, escrow, PITI, total/baseline interest,
interest saved, payoff month/date, and a full dated amortization schedule.

## What's here (bare minimum)
| Path | Role |
|------|------|
| `src/main/java/.../MainApp.java` | `@MainApplication` bootstrap (`AutoStart.main`) |
| `src/main/java/.../ExpandExtras.java` | helper `v1.mortgage.expand-extras` (build extras array; no finance) |
| `src/main/java/.../FormatSchedule.java` | helper `v1.mortgage.format-schedule` (dates + reshape; no finance) |
| `src/main/resources/graph/mortgage-calc.json` | the graph model (all the finance, in graph.math) |
| `src/main/resources/graphs.yaml` | CompileGraph manifest (`graph.model.automation`) |
| `src/main/resources/rest.yaml` | `POST /api/graph/{graph_id}` -> `graph-executor` flow |
| `src/main/resources/flows.yaml` + `flows/graph-executor.yml` | the executor flow |
| `src/main/resources/application.properties` | port, rest/flow/graph automation, health |

The only framework dependency is `minigraph-playground-engine` (the graph runtime + REST server).
