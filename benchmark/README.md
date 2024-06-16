# Benchmark Results

Generated on 6/16/2024, 4:57:02 AM

Version: 1.0.0-alpha10

### Test environment

System: linux x64

CPUs: AMD EPYC 7763 64-Core Processor x 4

Memory: 15.6 GB

### JVM Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2208.36 | ops/s |
| defineReflectionBindings | 5 | 2229.61 | ops/s |
| invokeDslBindings | 5 | 38556.92 | ops/s |
| invokeReflectionBindings | 5 | 42065.14 | ops/s |

### Kotlin/Native Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2138.04 | ops/sec |
| invokeDslBindings | 5 | 23461.06 | ops/sec |

### Notes

The engine creation times are included in define[Xzy]Bindings benchmarks, so the actual results should be much faster, but the relative results should remain the same.
