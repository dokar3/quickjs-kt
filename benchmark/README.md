# Benchmark Results

Generated on 7/23/2026, 3:52:09 AM

Version: 1.0.7

### Test environment

System: linux x64

CPUs: AMD EPYC 7763 64-Core Processor x 4

Memory: 15.6 GB

### JVM Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2312.92 | ops/s |
| defineReflectionBindings | 5 | 2330.66 | ops/s |
| invokeDslBindings | 5 | 28652.43 | ops/s |
| invokeReflectionBindings | 5 | 32417.86 | ops/s |

### Kotlin/Native Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2338.44 | ops/sec |
| invokeDslBindings | 5 | 21803.53 | ops/sec |

### Notes

The engine creation times are included in define[Xzy]Bindings benchmarks, so the actual results should be much faster, but the relative results should remain the same.
