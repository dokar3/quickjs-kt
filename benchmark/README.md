# Benchmark Results

Generated on 2/29/2024, 2:47:09 AM

Version: 1.0.0-alpha08

### Test environment

System: linux x64

CPUs: AMD EPYC 7763 64-Core Processor x 4

Memory: 15.6 GB

### JVM Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2213.51 | ops/s |
| defineReflectionBindings | 5 | 2203.63 | ops/s |
| invokeDslBindings | 5 | 11525.53 | ops/s |
| invokeReflectionBindings | 5 | 12197.72 | ops/s |

### Kotlin/Native Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2142.20 | ops/sec |
| invokeDslBindings | 5 | 9413.48 | ops/sec |

### Notes

The engine creation times are included in define[Xzy]Bindings benchmarks, so the actual results should be much faster, but the relative results should remain the same.
