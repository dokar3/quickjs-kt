# Benchmark Results

Generated on 7/28/2026, 7:11:44 AM

Version: 1.0.7

### Test environment

System: linux x64

CPUs: AMD EPYC 9V74 80-Core Processor x 4

Memory: 15.6 GB

### Binding

| Name | JVM | Kotlin/Native |
| --- | --- | --- |
| defineDslBindings | 2852.25 ops/s (5 iterations) | 2696.92 ops/sec (5 iterations) |
| defineReflectionBindings | 2798.30 ops/s (5 iterations) | — |
| evaluatePromisesWithBindingsSource | 272.02 ops/s (5 iterations) | 286.63 ops/sec (5 iterations) |
| invokeDslBindings | 29306.25 ops/s (5 iterations) | 22890.31 ops/sec (5 iterations) |
| invokeReflectionBindings | 31129.35 ops/s (5 iterations) | — |

### Pure execution

| Name | JVM | Kotlin/Native |
| --- | --- | --- |
| evaluateLargeSource | 197.43 ops/s (5 iterations) | 189.79 ops/sec (5 iterations) |
| evaluatePromisesSource | 1901.85 ops/s (5 iterations) | 1856.60 ops/sec (5 iterations) |
| evaluateTrivialSource | 61379.10 ops/s (5 iterations) | 45426.68 ops/sec (5 iterations) |
| executeAllocationBytecode | 8.39 ops/s (5 iterations) | 8.36 ops/sec (5 iterations) |
| executeArrayOperationsBytecode | 60.56 ops/s (5 iterations) | 63.62 ops/sec (5 iterations) |
| executeBigIntBytecode | 1583.39 ops/s (5 iterations) | 1562.63 ops/sec (5 iterations) |
| executeFibonacciBytecode | 388.22 ops/s (5 iterations) | 402.84 ops/sec (5 iterations) |
| executeFunctionCallsBytecode | 87.99 ops/s (5 iterations) | 88.91 ops/sec (5 iterations) |
| executeJsonBytecode | 88.84 ops/s (5 iterations) | 89.11 ops/sec (5 iterations) |
| executeLoopsBytecode | 67.61 ops/s (5 iterations) | 68.41 ops/sec (5 iterations) |
| executeMapSetBytecode | 73.49 ops/s (5 iterations) | 68.91 ops/sec (5 iterations) |
| executeRegExpBytecode | 241.45 ops/s (5 iterations) | 254.31 ops/sec (5 iterations) |
| executeTypedArrayBytecode | 73.89 ops/s (5 iterations) | 74.46 ops/sec (5 iterations) |

### Notes

The engine creation times are included in define*Bindings benchmarks, so the actual results should be much faster, but the relative results should remain the same.
