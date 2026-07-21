# Benchmark Results

Generated on 7/21/2026, 7:49:44 AM

Version: 1.0.5

### Test environment

System: linux x64

CPUs: AMD EPYC 7763 64-Core Processor x 4

Memory: 15.6 GB

### JVM Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2312.67 | ops/s |
| defineReflectionBindings | 5 | 2289.06 | ops/s |
| invokeDslBindings | 5 | 34611.40 | ops/s |
| invokeReflectionBindings | 5 | 36914.78 | ops/s |

### Kotlin/Native Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2323.69 | ops/sec |
| invokeDslBindings | 5 | 25364.32 | ops/sec |

### Notes

The engine creation times are included in define[Xzy]Bindings benchmarks, so the actual results should be much faster, but the relative results should remain the same.
