# Benchmark Results

Generated on 6/18/2024, 3:47:00 AM

Version: 1.0.0-alpha11

### Test environment

System: linux x64

CPUs: AMD EPYC 7763 64-Core Processor x 4

Memory: 15.6 GB

### JVM Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2221.15 | ops/s |
| defineReflectionBindings | 5 | 2225.01 | ops/s |
| invokeDslBindings | 5 | 38466.47 | ops/s |
| invokeReflectionBindings | 5 | 41658.27 | ops/s |

### Kotlin/Native Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2140.30 | ops/sec |
| invokeDslBindings | 5 | 23963.17 | ops/sec |

### Notes

The engine creation times are included in define[Xzy]Bindings benchmarks, so the actual results should be much faster, but the relative results should remain the same.
