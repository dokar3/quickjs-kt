# Benchmark Results

Generated on 6/22/2024, 2:29:55 AM

Version: 1.0.0-alpha11

### Test environment

System: linux x64

CPUs: AMD EPYC 7763 64-Core Processor x 4

Memory: 15.6 GB

### JVM Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2204.72 | ops/s |
| defineReflectionBindings | 5 | 2237.23 | ops/s |
| invokeDslBindings | 5 | 39028.09 | ops/s |
| invokeReflectionBindings | 5 | 41608.62 | ops/s |

### Kotlin/Native Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2137.75 | ops/sec |
| invokeDslBindings | 5 | 24056.96 | ops/sec |

### Notes

The engine creation times are included in define[Xzy]Bindings benchmarks, so the actual results should be much faster, but the relative results should remain the same.
