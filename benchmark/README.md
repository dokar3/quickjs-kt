# Benchmark Results

Generated on 6/29/2024, 8:22:30 AM

Version: 1.0.0-alpha11

### Test environment

System: linux x64

CPUs: AMD EPYC 7763 64-Core Processor x 4

Memory: 15.6 GB

### JVM Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2211.65 | ops/s |
| defineReflectionBindings | 5 | 2230.20 | ops/s |
| invokeDslBindings | 5 | 38399.53 | ops/s |
| invokeReflectionBindings | 5 | 41724.12 | ops/s |

### Kotlin/Native Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2169.57 | ops/sec |
| invokeDslBindings | 5 | 23980.27 | ops/sec |

### Notes

The engine creation times are included in define[Xzy]Bindings benchmarks, so the actual results should be much faster, but the relative results should remain the same.
