# Benchmark Results

Generated on 2/29/2024, 6:14:05 AM

Version: 1.0.0-alpha08

### Test environment

System: linux x64

CPUs: AMD EPYC 7763 64-Core Processor x 4

Memory: 15.6 GB

### JVM Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2260.13 | ops/s |
| defineReflectionBindings | 5 | 2249.12 | ops/s |
| invokeDslBindings | 5 | 39966.64 | ops/s |
| invokeReflectionBindings | 5 | 42472.55 | ops/s |

### Kotlin/Native Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2166.79 | ops/sec |
| invokeDslBindings | 5 | 25079.77 | ops/sec |

### Notes

The engine creation times are included in define[Xzy]Bindings benchmarks, so the actual results should be much faster, but the relative results should remain the same.
