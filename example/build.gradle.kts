import org.jetbrains.bitcodetools.plugin.DecompileBitcodeTask
import org.jetbrains.bitcodetools.plugin.ExtractBitcodeTask

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.bitcodetools.plugin")
}

group = "me.user"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    listOf(macosX64(), macosArm64(), mingwX64(), linuxX64()).forEach {
        it.binaries {
            executable {
                entryPoint = "main"
            }
        }
    }

    sourceSets {
        val nativeMain by creating
        val nativeTest by creating

        val macosX64Main by getting {
            dependsOn(nativeMain)
        }
        val macosArm64Main by getting {
            dependsOn(nativeMain)
        }
        val linuxX64Main by getting {
            dependsOn(nativeMain)
        }
        val mingwX64Main by getting {
            dependsOn(nativeMain)
        }

        val macosX64Test by getting {
            dependsOn(nativeTest)
        }
        val macosArm64Test by getting {
            dependsOn(nativeTest)
        }
        val linuxX64Test by getting {
            dependsOn(nativeTest)
        }
        val mingwX64Test by getting {
            dependsOn(nativeTest)
        }
    }
}

/* Configure the bitcode-analysis pipeline for your Kotlin/Native project:
 * `decompileBitcode` and `decompileDebugBitcode` tasks;
 * `extractBitcode` and `extractDebugBitcode` tasks.
 * They are connected to the project and to each other,
 * so do not use them to analyze some stand-alone code.
 */

decompileBitcodeConfig {
    val hostOs: String = System.getProperty("os.name")
    val arch: String = System.getProperty("os.arch")
    linkTaskName = when {
        hostOs == "Linux" -> "linkReleaseExecutableLinuxX64"
        hostOs == "Mac OS X" && arch == "x86_64" -> "linkReleaseExecutableMacosX64"
        hostOs == "Mac OS X" && arch == "aarch64" -> "linkReleaseExecutableMacosArm64"
        hostOs.startsWith("Windows") -> throw GradleException("Windows is currently unsupported: unable to install `llvm-dis` tool")
        else -> throw GradleException("Unsupported target platform: $hostOs / $arch")
    }
    linkDebugTaskName = linkTaskName.replace("Release", "Debug")
    setCompilerFlags = { compilerFlags ->
        kotlin {
            listOf(macosX64(), macosArm64(), mingwX64(), linuxX64()).forEach {
                it.compilations.getByName("main") {
                    kotlinOptions.freeCompilerArgs += compilerFlags
                }
            }
        }
        println("[example] following compiler flags are set: $compilerFlags")
    }
    artifactsDirectoryPath = "build/bitcode"
    llOutputFileName = "example-bitcode.ll"
    llDebugOutputFileName = "example-bitcode-debug.ll"
}

extractFromDecompiledBitcodeConfig {
    functionNames = listOf("kfun:#main(){}", "ThrowIllegalArgumentException")
    functionPatterns = listOf(".*main.*")
    linePatterns = listOf(
        "%2 = icmp eq i64 %1, 0",
        ".*call.*kfun:.*#hashCode\\(\\)\\{\\}kotlin\\.Int.*"
    )
    ignorePatterns = listOf("kfun:kotlin.*")
    recursionDepth = 1u
    verbose = true
}

/* The stand-alone tasks are already registered.
 * However, you can make them more convenient:
 * configure the default values of the most frequently used arguments here and
 * define others & override the configured ones in the command line.
 */

tasks.named<DecompileBitcodeTask>("decompileSomeBitcode") {
    outputFilePath = "build/bitcode/standalone-bitcode.ll"
}

tasks.named<ExtractBitcodeTask>("extractSomeBitcode") {
    recursionDepth = 1u
    inputFilePath = "build/bitcode/standalone-bitcode.ll"
    verbose = true
}

/* Register new custom ExtractBitcodeTask &
 * configure it with the full power of Gradle.
 */

tasks.register<ExtractBitcodeTask>("extractCustomBitcode") {
    dependsOn("decompileBitcode")
    doFirst {
        println("It's impolite not to say hello at the very beginning. So, hello!")
    }
    inputFilePath = "build/bitcode/bitcode.ll"
    outputFilePath = "build/bitcode/custom-bitcode.ll"
    functionNames = listOf("kfun:#main(){}")
}
