# Bitcode tools for Kotlin/Native projects 🄺🄽🐘

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Contributions welcome](https://img.shields.io/badge/Contributions-welcome-brightgreen.svg?style=flat)](#contributing--developing)
[![Checks](https://github.com/GlebSolovev/bitcode-tools/actions/workflows/checks.yaml/badge.svg?branch=main)](https://github.com/GlebSolovev/bitcode-tools/actions/workflows/checks.yaml)

A simple **Gradle plugin** that provides tasks **to obtain and analyze bitcode** for any Kotlin/Native projects.

*Authors:* Gleb Solovev, Evgenii Moiseenko, and Anton Podkopaev, [Programming Languages and Program Analysis (PLAN) Lab](https://lp.jetbrains.com/research/plt_lab/) at JetBrains Research.

- [Bitcode tools for Kotlin/Native projects 🄺🄽🐘](#bitcode-tools-for-kotlinnative-projects-)
  - [TL;DR](#tldr)
  - [Why analyze bitcode?](#why-analyze-bitcode)
  - [Key features](#key-features)
  - [Set up necessary dependencies](#set-up-necessary-dependencies)
    - [Python dependencies](#python-dependencies)
    - [LLVM dependency](#llvm-dependency)
  - [Apply plugin](#apply-plugin)
    - [Clone project locally](#clone-project-locally)
    - [Apply by id](#apply-by-id)
  - [Set up plugin](#set-up-plugin)
    - [Required set-up](#required-set-up)
    - [Access debug tasks](#access-debug-tasks)
    - [Select `extractBitcode` targets](#select-extractbitcode-targets)
    - [Set up `extractBitcode` working mode](#set-up-extractbitcode-working-mode)
    - [Configure directories and file names](#configure-directories-and-file-names)
  - [Plugin configuration examples](#plugin-configuration-examples)
    - [Minimal configuration](#minimal-configuration)
    - [Enable debug tasks](#enable-debug-tasks)
    - [Select which targets and how to extract](#select-which-targets-and-how-to-extract)
    - [Customize directories and file names](#customize-directories-and-file-names)
  - [Call tasks in the command line](#call-tasks-in-the-command-line)
    - [Examples](#examples)
  - [Advanced customization](#advanced-customization)
    - [Configure stand-alone tasks](#configure-stand-alone-tasks)
    - [Create your own tasks](#create-your-own-tasks)
  - [Contributing \& developing](#contributing--developing)

## TL;DR

Run through the README quickly! ⚡️

* Get to know the [key features](#key-features) of the plugin.
* Make sure all [necessary dependencies](#set-up-necessary-dependencies) are set.
* [Apply the plugin](#apply-plugin) to your Kotlin/Native project.
* Check the examples of the [plugin configuration](#plugin-configuration-examples) and [task calls](#examples) to quickly master the plugin. 
  * An [example Kotlin/Native project](./example/build.gradle.kts) configured with the plugin might also be helpful.

## Why analyze bitcode?

The pipeline for compiling Kotlin/Native code into an executable binary is as follows: first, the code is compiled into LLVM bitcode &mdash; a special assembly-like language used by the framework &mdash; and then LLVM converts the bitcode into the final output file.

While some optimizations happen at the very last stage, a huge number of them (including all Kotlin-specific optimizations) happen during compilation to bitcode. Therefore, bitcode analysis of Kotlin/Native code ***becomes especially useful for***:
* exploration of optimizations and transformations applied to the code;
* debugging the compilation process.

In general, bitcode analysis of Kotlin/Native projects is in some ways quite similar to bytecode analysis of Kotlin/JVM code. However, since the first one is much more difficult to obtain and explore manually, the current plugin is here to help you! 🦸 

## Key features

Use consice Kotlin DSL syntax to configure the plugin in your `build.gradle.kts` file and get bitcode analysis tasks in return.
* ***Analyze bitcode of your Kotlin/Native project.***
  * `decompileBitcode` &mdash; builds a human-readable `.ll` bitcode file of your source code;
  * `extractBitcode` &mdash; extracts the specified elements from the project's bitcode;
  * *(optional)* `decompileBitcodeDebug` and `extractBitcodeDebug` &mdash; additional versions of above tasks to analyze bitcode of your project built in the debug mode.
* ***Analyze any bitcode files.***
  * `decompileSomeBitcode` &mdash; decompiles a bitcode `*.bc` file into a human-readable `*.ll` one;
  * `extractSomeBitcode` &mdash; extracts the specified elements from a bitcode `*.ll` file.
* ***Create your own custom tasks*** using `DecompileBitcodeTask` and `ExtractBitcodeTask` classes.

Of course, all tasks provided by the plugin...
* ...support accurate inputs/outputs tracking &mdash; meaning that actual work will only be done when necessary 😴; 
* ...are properly linked to the project's build tasks &mdash; so no thinking about working pipeline is needed, just call the tasks 🤝;
* ...provide set-up of their parameters both in the build file and in the command line &mdash; communicate with the plugin in the most convenient way for you 🫂. 

## Set up necessary dependencies

The plugin requires two dependencies installed on your machine to work properly: **Python with necessary modules** and **LLVM**.

### Python dependencies

First, make sure you have Python compatible with the `3.10` version on your computer.
```bash
python3 --version
```

Then install the `llvmlite` module.
```bash
pip3 install llvmlite~=0.41.0
```

### LLVM dependency

The key and the only one dependency needed from LLVM is the `llvm-dis` tool. Unfortunately, that requires installing the complete LLVM distribution and, unfortunately, we don't know any way to do it easily on the *Windows* machines so far. 

To install the LLVM on your machine it is recommended to check the various guides on the Internet.

Once you finish, make sure LLVM is accessible and is compatible with the `14.0.0` version.
```bash
llvm-config --version
```

## Apply plugin

The standard way to install a Gradle plugin is to obtain it from *Maven Repository* automatically. Unfortunately, the bitcode-analysis plugin has not been published there so far. Therefore, the only way to install it is to clone this repository locally. 

### Clone project locally

Clone this repository into a new directory locally. It's recommended not to make it a subproject of some Gradle project, since it may require additional configuration.
```bash
# clones the repo into a new folder `bitcode-analysis-plugin`
git clone https://github.com/GlebSolovev/bitcode-tools.git bitcode-analysis-plugin
```
Now link the folder with the plugin repository to your Kotlin/Native project. To do that, add the following code into the `settings.gradle.kts` file of your project.
```kotlin
pluginManagement {
    includeBuild("absolute-path-to-the-bitcode-analysis-plugin")
}
```

### Apply by id

Finally, add the plugin to your `build.gradle.kts` by its id. Here is an example.
```kotlin
plugins {
    kotlin("multiplatform")
    // ... other plugins you might have
    id("org.jetbrains.bitcodetools.plugin")
}
``` 
If your working in IDE, it'd better to rebuild Gradle at this point, so to access lovely DSL auto-completion.

## Set up plugin

The next step is plugin configuration. There are two extensions available in `build.gradle.kts` to do this: `decompileBitcodeConfig` and `extractFromDecompiledBitcodeConfig`.

### Required set-up

The only set-up being required is the following one:
```kotlin
decompileBitcodeConfig {
    linkTaskName = "the name of the task to link your Kotlin/Native sources" 
    setCompilerFlags = { compilerFlags: List<String> ->
        // add `compilerFlags` to your Kotlin/Native compiler
    }
}
```
Check [examples section](#plugin-configuration-examples) to see ready-to-use code snippets. 

Now plugin already provides `decompileBitcode` and `extractBitcode` tasks to analyze your project's bitcode and `decompileSomeBitcode` and `extractSomeBitcode` tasks to analyze some standalone bitcode files.

### Access debug tasks

To get `decompileBitcodeDebug` and `extractBitcodeDebug` tasks to analyze debug build of your project, the `linkDebugTaskName` should be set too.
```kotlin
decompileBitcodeConfig {
    // ... other properties set-up
    linkDebugTaskName = "the name of the task to link your Kotlin/Native sources in the debug mode"
}
```

### Select `extractBitcode` targets

`extractBitcode` and `extractBitcodeDebug` tasks extracts the target functions defined in the `extractFromDecompiledBitcodeConfig`. So far, you can select the targets in the following way.
```kotlin
extractFromDecompiledBitcodeConfig {
    functionNames = listOf("name of the function, specified exactly as in the bitcode file")
    functionPatterns = listOf("regex pattern to match the desired function names")
    linePatterns = listOf("regex pattern to match at least one line of bitcode of the desired functions")
    ignorePatterns = listOf("regex pattern to ignore functions with their name matching it")
}
```
Since all these properties are `ListProperty`-s, you can always add new elements to them with `.add(...)` syntax.
```kotlin
functionNames.add("desired function names")
```  
See more examples in the [examples section](#plugin-configuration-examples).

### Set up `extractBitcode` working mode

Extract-bitcode tasks provide several properties, to configure the way the perform the extraction.

The property `recursionDepth` enables ***recursive exraction*** of all called functions up to the specified depth (relative to the target functions). Zero value (the default one) means recursive extraction is disabled: only target functions will be extracted.
```kotlin
extractFromDecompiledBitcodeConfig {
    // ... other properties set-up
    recursionDepth = 1u // additionaly extracts all functions called from the target functions 
}
```

The property `verbose` simply enables logging printed to the stdout. It can be useful to track the extraction process thoroughly, but might be too verbose in case you extract a lot of functions at a time.
```kotlin
extractFromDecompiledBitcodeConfig {
    // ... other properties set-up
    verbose = true // enables logging (disabled by default)
}
```

### Configure directories and file names

Even though the plugin by default uses the most common names and paths for the input and output files, you still might want to customize them. To do this, consider the following properties. 

```kotlin
decompileBitcodeConfig {
    // ... other properties set-up
    artifactsDirectoryPath = "path to the directory to store all the input and output bitcode artifacts (relative to the project directory), 'build/bitcode' by default"
    bcInputFileName = "name of the '*.bc' file produced by the link task, 'out.bc' by default"
    llOutputFileName = "name of the '*.ll' file to decompile bitcode into, 'bitcode.ll' by default"
    llDebugOutputFileName = "name of the '*.ll' file to decompile debug bitcode into, 'bitcode-debug.ll' by default"
}

extractFromDecompiledBitcodeConfig {
    // ... other properties set-up
    outputFileName = "name of the file to save extracted bitcode into, 'extracted-bitcode.ll' by default"
    debugOutputFileName = "name of the file to save extracted debug bitcode into, 'extracted-bitcode-debug.ll' by default"
}
```
However, if you experiment with bitcode a lot generating many different result files for different configurations, it might be more convenient to set up the names of the files via command line flags. Check the [command-line section](#call-tasks-in-the-command-line). 

## Plugin configuration examples

Here you can find several ready-to-use code snippets, which also clarify the syntax described above. Besides, you can find *an example Kotlin/Native project configured with the plugin* at the [`example`](example/build.gradle.kts) directory.

### Minimal configuration

Minimal configuration to build and analyze bitcode of a standard Kotlin/Native project on a `LinuxX64` machine.

```kotlin
decompileBitcodeConfig {
    linkTaskName = "linkReleaseExecutableLinuxX64"
    setCompilerFlags = { compilerFlags ->
        kotlin {
            linuxX64().compilations.getByName("main") {
                kotlinOptions.freeCompilerArgs += compilerFlags
            }
        }
    }
}
```

Minimal configuration for a standard Kotlin/Native project to support any machine architecture.
```kotlin
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
    setCompilerFlags = { compilerFlags ->
        kotlin {
            listOf(macosX64(), macosArm64(), mingwX64(), linuxX64()).forEach {
                it.compilations.getByName("main") {
                    kotlinOptions.freeCompilerArgs += compilerFlags
                }
            }
        }
    }
}
```

### Enable debug tasks

An easy way to get the debug tasks for a standard Kotlin/Native project.
```kotlin
decompileBitcodeConfig {
    // ... other properties set-up
    linkDebugTaskName = linkTaskName.replace("Release", "Debug")
}
```

### Select which targets and how to extract

An example of selecting the targets to extract from the project's bitcode.
```kotlin
extractFromDecompiledBitcodeConfig {
    // extract these two functions: main and exception-throwing
    functionNames = listOf("kfun:#main(){}", "ThrowIllegalArgumentException")

    // additionally, extract all the functions that contain `main` in their names as a substring
    functionPatterns = listOf(".*main.*")

    // also extract functions that either contain the following exact code line or rather any call to some `hashCode` function
    linePatterns.add("%2 = icmp eq i64 %1, 0")
    linePatterns.add(".*call.*kfun:.*#hashCode\\(\\)\\{\\}kotlin\\.Int.*")

    // ignore functions from the standard library, otherwise they most likely litter the analysis
    ignorePatterns.add("kfun:kotlin.*")
}
```

Tune the behaviour of the `extractBitcode` and `extractBitcodeDebug` tasks.
```kotlin
extractFromDecompiledBitcodeConfig {
    // ... other properties set-up

    // choose which depth makes sense for you to examine the calls inside the target functions
    recursionDepth = 3u

    // track the extraction process via the log messages
    verbose = true
}
```

### Customize directories and file names 

Define custom file directories and file names for the tasks.
```kotlin
decompileBitcodeConfig {
    // ... other properties set-up
    
    // defines the parent directory for all the bitcode artifacts generated by the pipeline tasks, relative to the project's root
    artifactsDirectoryPath = "build/customBitcodeDir"

    // be careful changing this file name: your task provided in the `linkTask` should generate exactly this file in the provided directory via `-Xtemporary-files-dir` flag (it's easier just to check it on practice by running the `decompileBitcode` task)
    bcInputFileName = "main.bc"

    // choose the file names you like, the decompiled bitcode will appear in these files in the `artifactsDirectoryPath` directory
    llOutputFileName = "decompiled-bitcode.ll"
    llDebugOutputFileName = "decompiled-bitcode-debug.ll"
}

extractFromDecompiledBitcodeConfig {
    // ... other properties set-up

    // simple customization of the output files names, they will be generated in the `artifactsDirectoryPath` too
    outputFileName = "extracted-bitcode.ll"
    debugOutputFileName = "extracted-bitcode-debug.ll"

    // P.S. you can't change the input file name, because `extractBitcode` / `extractBitcodeDebug` tasks are pipeline ones: they accept `llOutputFileName` / `llDebugOutputFileName` from the `decompileBitcode` / `decompileBitcodeDebug` tasks as an input
}
```

## Call tasks in the command line

As for any other Gradle tasks, one of the easiest way to run the bitcode ones is to call them via `gradle` / `./gradlew` from the command line. Also if your IDE supports Gradle tasks execution from the GUI, this could also be an option.

```bash
# runs `decompileBitcode` task found in the project
gradle decompileBitcode

# does the same, you just use the script in the root directory to call Gradle
./gradlew decompileBitcode

# runs `decompileBitcode` task for the `example` subproject
gradle :example:decompileBitcode
```

While the plugin set-up defines the default arguments of the tasks (so they can be called just as-is), these arguments can be overriden by ones specified in the command line. 

Almost all settings from the set-up section can be passed by the command line flags. To check their full list just call the `help` task.

```bash
gradle help --task decompileBitcode
gradle help --task extractBitcode
```

### Examples

Calling `decompileBitcode` / `decompileDebugBitcode` / `decompileSomeBitcode` tasks.

```bash
# call with arguments configured in the build files
gradle decompileBitcode

# override input and output file paths
gradle decompileBitcode --input build/bitcode/releaseSources/out.bc --output build/bitcode/bitcode.ll
```

Calling `extractBitcode` / `extractBitcodeDebug` / `extractSomeBitcode` tasks.

```bash
# call with arguments configured in the build files
gradle extractBitcode

# override input (*) and output file paths
# note: the input path should match the output file of the corresponding `decompileBitcode` task
gradle extractBitcode --input build/bitcode/bitcode.ll --output build/bitcode/extracted-bitcode.ll

# select targets to extract: 
    # main and exception-throwing functions;
    # all the functions that contain `main` in their names as a substring;
    # all the functions that contain call to some `hashCode` function;
    # ignoring functions from the standard library
# note: don't forget to quote the arguments, otherwise your console may try to interpret them as regexes by itself
gradle extractBitcode --function 'kfun:#main(){}' --function 'ThrowIllegalArgumentException' --function-pattern '.*main.*' --line-pattern '.*call.*kfun:.*#hashCode\(\)\{\}kotlin\.Int.*' --ignore-function-pattern 'kfun:kotlin.*'

# extract main function and all functions that are called from its body with the detailed logging enabled
gradle extractBitcode --function-pattern 'kfun:#main\(.*' --recursion-depth=1 --verbose
```

## Advanced customization

Two Gradle extensions `decompileBitcodeConfig` and `extractFromDecompiledBitcodeConfig` provide a great way to set up the pipeline tasks to analyze your project's bitcode. But there is still place for the customization of the stand-alone `decompileSomeBitcode` and `extractSomeBitcode` tasks and even creating your own Gradle machinery.

### Configure stand-alone tasks

If you tend to use the same arguments of the stand-alone task frequently, they can be moved to the `build.gradle.kts` as default ones just for convenience. Here is an example.

```kotlin
tasks.named<DecompileBitcodeTask>("decompileSomeBitcode") {
    outputFilePath = "build/bitcode/bitcode.ll"
}

tasks.named<ExtractBitcodeTask>("extractSomeBitcode") {
    recursionDepth = 1u
    verbose = true
}
```

### Create your own tasks

All tasks provided by the plugin are of the task classes `DecompileBitcodeTask` and `ExtractBitcodeTask`, actually the plugin only makes their set-up easier. Thus, if you feel the provided tasks are not enough for your goals, you can always register your own ones and freely configure them with all the power of Gradle!

```kotlin
tasks.register<DecompileBitcodeTask>("decompileMyBitcode") {}

tasks.register<ExtractBitcodeTask>("extractMyBitcode") {
    verbose = True
}

tasks.register<ExtractBitcodeTask>("extractBitcodePolitely") {
    dependsOn("decompileBitcode")
    doFirst {
        println("It's impolite not to say hello at the very beginning. So, hello!")
    }
    inputFilePath = "build/bitcode/bitcode.ll"
    outputFilePath = "build/bitcode/custom-bitcode.ll"
    functionNames = listOf("kfun:#main(){}")
}
```
Of course, all the `DecompileBitcodeTaks` and `ExtractBitcodeTask` tasks get command-line flags support just out-of-the-box, so there is no limitations on calling your custom tasks in the command line.

## Contributing & developing

If you have any ideas on improving the project or found any bugs, you're always welcome to contact any of the authors or support an issue 🫂

In case you're interested in the more development details of this project, make sure to check the [DEVELOPMENT_GUIDE.md](./DEVELOPMENT_GUIDE.md).
