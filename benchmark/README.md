# Benchmark Results

Generated on 7/28/2026, 4:50:47 AM

Version: 1.0.7

### Test environment

System: linux x64

CPUs: AMD EPYC 7763 64-Core Processor x 4

Memory: 15.6 GB

### Binding

| Name | JVM | Kotlin/Native |
| --- | --- | --- |
| defineDslBindings | 2335.99 ops/s (5 iterations) | 2299.84 ops/sec (5 iterations) |
| defineReflectionBindings | 2331.72 ops/s (5 iterations) | — |
| evaluatePromisesWithBindingsSource | 236.91 ops/s (5 iterations) | 265.84 ops/sec (5 iterations) |
| invokeDslBindings | 29127.10 ops/s (5 iterations) | 22596.30 ops/sec (5 iterations) |
| invokeReflectionBindings | 32069.04 ops/s (5 iterations) | — |

### Pure execution

| Name | JVM | Kotlin/Native |
| --- | --- | --- |
| evaluateLargeSource | 231.55 ops/s (5 iterations) | 232.03 ops/sec (5 iterations) |
| evaluatePromisesSource | 1793.89 ops/s (5 iterations) | 1778.45 ops/sec (5 iterations) |
| evaluateTrivialSource | 66709.94 ops/s (5 iterations) | 49656.11 ops/sec (5 iterations) |
| executeAllocationBytecode | 8.80 ops/s (5 iterations) | 9.09 ops/sec (5 iterations) |
| executeArrayOperationsBytecode | 52.27 ops/s (5 iterations) | 51.62 ops/sec (5 iterations) |
| executeBigIntBytecode | 1369.95 ops/s (5 iterations) | 1394.97 ops/sec (5 iterations) |
| executeFibonacciBytecode | 286.16 ops/s (5 iterations) | 276.17 ops/sec (5 iterations) |
| executeFunctionCallsBytecode | 71.81 ops/s (5 iterations) | 71.49 ops/sec (5 iterations) |
| executeJsonBytecode | 75.85 ops/s (5 iterations) | 77.24 ops/sec (5 iterations) |
| executeLoopsBytecode | 50.97 ops/s (5 iterations) | 51.33 ops/sec (5 iterations) |
| executeMapSetBytecode | 57.92 ops/s (5 iterations) | 59.06 ops/sec (5 iterations) |
| executeRegExpBytecode | 226.70 ops/s (5 iterations) | 212.32 ops/sec (5 iterations) |
| executeTypedArrayBytecode | 63.39 ops/s (5 iterations) | 63.72 ops/sec (5 iterations) |

### Notes

The engine creation times are included in define*Bindings benchmarks, so the actual results should be much faster, but the relative results should remain the same.
