# Fix Kotlin Compiler OutOfMemoryError

The project build is failing with `java.lang.OutOfMemoryError: GC overhead limit exceeded` during Kotlin compilation. This indicates that the Kotlin compiler daemon (which uses the K2/FIR backend) is running out of memory or spending too much time in Garbage Collection.

## Proposed Changes

### Build Configuration

#### [MODIFY] [gradle.properties](file:///D:/Studio%20Projects/Jaat-Player_v1/gradle.properties)

Increase the heap size for both the Gradle daemon and the Kotlin compiler daemon. Also, switch to `ParallelGC` which is generally more efficient for high-memory-pressure build environments.

- Increase `org.gradle.jvmargs` from `-Xmx6g` to `-Xmx8g`.
- Increase `kotlin.daemon.jvmargs` from `-Xmx8g` to `-Xmx12g`.
- Add `-XX:+UseParallelGC` to both to improve GC performance.

```properties
org.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=1g -XX:+UseParallelGC -Dfile.encoding=UTF-8
kotlin.daemon.jvmargs=-Xmx12g -XX:+UseParallelGC
```

## Verification Plan

### Automated Tests
- Run the build command that was failing:
  `./gradlew :app:compileArm64FossDebugKotlin`
- Verify that the build completes successfully without OOM.

### Manual Verification
- Monitor the memory usage of the `KotlinCompileDaemon` process during build to ensure it stays within limits.
