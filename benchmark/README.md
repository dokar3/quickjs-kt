# Benchmark Results

Generated on 7/2/2024, 1:39:51 PM

Version: 1.0.0-alpha12

### Test environment

System: linux x64

CPUs: AMD EPYC 7763 64-Core Processor x 4

Memory: 15.6 GB

### JVM Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2211.57 | ops/s |
| defineReflectionBindings | 5 | 2211.81 | ops/s |
| invokeDslBindings | 5 | 37959.13 | ops/s |
| invokeReflectionBindings | 5 | 41269.80 | ops/s |

### Kotlin/Native Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2147.95 | ops/sec |
| invokeDslBindings | 5 | 23507.90 | ops/sec |

### Notes

The engine creation times are included in define[Xzy]Bindings benchmarks, so the actual results should be much faster, but the relative results should remain the same.
