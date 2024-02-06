# JNI

Using zig for cross-compiling, based on [zig-cross](https://github.com/mrexodia/zig-cross)

### Requirements

- [zig](https://ziglang.org/)
- Windows x64 JDK and Linux x64 JDK
- [CMake](https://cmake.org/)
- Android SDK

### Build

```bash
# Build Linux x64
cmake ./ -B build/linux_x64 -G Ninja -DTARGET_PLATFORM=linux_x64 -DPLATFORM_JAVA_HOME=/path/to/linux_x64/java_home
cmake --build ./build/linux_x64

# Build Windows x64
cmake ./ -B build/windows_x64 -G Ninja -DTARGET_PLATFORM=windows_x64 -DPLATFORM_JAVA_HOME=/path/to/windows_x64/java_home
cmake --build ./build/windows_x64
```

