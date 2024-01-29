plugins {
    kotlin("multiplatform")
    id("com.glebsolovev.kotlin.bitcodetools.gradle.plugin")
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
    artifactsDirectoryPath = "build/bitcode"
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
    llOutputFileName = "example-bitcode.ll"
    llDebugOutputFileName = "example-bitcode-debug.ll"
}

extractFromDecompiledBitcodeConfig {
    functionsToExtractNames = listOf("kfun:#main(){}")
    functionsToExtractNames.add("ThrowIllegalArgumentException")
    functionsToExtractPatterns.add(".*main.*")
    functionsToIgnorePatterns.add("kfun:kotlin.*")
    recursionDepth = 1u
    verbose = true
}
