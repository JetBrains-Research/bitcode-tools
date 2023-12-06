# Example Kotlin/Native project

This small project is used just to validate and show plugin's work.

## Validate plugin

You can find `DecompileBitcodePlugin` connected and configured in `build.gradle.kts`. It provides `decompileBitcode` task that can be executed from this (i.e. `example`) directory in the following way:
```bash
../gradlew :example:decompileBitcode
``` 
Then you can find generated bitcode of `Main.kt` in the `build/bitcode/bitcode.ll` file.

## Run Kotlin/Native code

If you'd like to run `Main.kt` just as a Kotlin/Native program, execute one of the following Gradle tasks:
```bash
../gradlew :example:runDebugExecutableLinuxX64 
../gradlew :example:runReleaseExecutableLinuxX64
```
and check the console's output.
