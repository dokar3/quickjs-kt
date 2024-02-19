# Benchmark Results

Generated on 2/19/2024, 1:02:12 PM

### Test environment

System: linux x64

CPUs: AMD EPYC 7763 64-Core Processor x 4

Memory: 15.6 GB

### JVM Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2204.60 | ops/s |
| defineReflectionBindings | 5 | 2220.90 | ops/s |
| invokeDslBindings | 5 | 40439.02 | ops/s |
| invokeReflectionBindings | 5 | 42550.95 | ops/s |

### Kotlin/Native Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
| defineDslBindings | 5 | 2145.06 | ops/sec |
| invokeDslBindings | 5 | 23855.52 | ops/sec |

### Notes

The engine creation times are included in define[Xzy]Bindings benchmarks, so the actual results should be much faster, but the relative results should remain the same.
