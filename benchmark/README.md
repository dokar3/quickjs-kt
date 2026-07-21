# Benchmark Results

Generated on 7/21/2026, 9:08:40 AM

Version: 1.0.5

### Test environment

System: linux x64

CPUs: AMD EPYC 7763 64-Core Processor x 4

Memory: 15.6 GB

### JVM Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2311.47 | ops/s |
| defineReflectionBindings | 5 | 2317.27 | ops/s |
| invokeDslBindings | 5 | 30155.27 | ops/s |
| invokeReflectionBindings | 5 | 33052.59 | ops/s |

### Kotlin/Native Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2307.72 | ops/sec |
| invokeDslBindings | 5 | 22899.91 | ops/sec |

### Notes

The engine creation times are included in define[Xzy]Bindings benchmarks, so the actual results should be much faster, but the relative results should remain the same.
