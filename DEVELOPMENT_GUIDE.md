# Development guide

In this document you can find some technical details about the inside of the project that might help you develop or extend it.

- [Development guide](#development-guide)
  - [Created from the Gradle-plugins template](#created-from-the-gradle-plugins-template)
  - [Project's structure](#projects-structure)
  - [Useful Gradle tasks](#useful-gradle-tasks)
  - [GitHub CI](#github-ci)
  - [Future plans](#future-plans)


## Created from the Gradle-plugins template

First of all, this repository is based on the [kotlin-gradle-plugin-template](https://github.com/cortinico/kotlin-gradle-plugin-template). 

That means the following items are organized the same:
* project's structure;
* tasks to lint, build and publish the plugin;
* GitHub CI.

Therefore, you can read more about them in [the template's README.md](https://github.com/cortinico/kotlin-gradle-plugin-template/blob/main/README.md). Nevertheless, the most important notes can be found in the next sections of this document.

## Project's structure

Here is a brief overview of where different modules of the project currently live.
* [`plugin-build`](./plugin-build/) is the directory devoted to the implementation and the build processes of the plugin.
  * In its [`plugin`](./plugin-build/plugin/) subfolder you can find the implementation code of the plugin, together with its tests. The actual code lives in the [`src`](./plugin-build/plugin/src/) subfolder.  
  * The implementation code of the plugin, the extensions and the tasks it provides is located in the [`main/java`](./plugin-build/plugin/src/main/java/) directory, while in the [`main/resources`](./plugin-build/plugin/src/main/resources/) folder you can find the Python script for the `ExtractBitcodeTask` implementation.
* [`example`](./example/) is an actual small Kotlin/Native example project that applies the implemented plugin. Although the project can be used for demonstration purposes, it is especially useful for testing. So far all the tests executed in CI call the plugin tasks of the `example` project.

The other directories and files are devoted to the build processes of the project. They should work out-of-the-box, so hopefully you'll never need to explore them.

## Useful Gradle tasks 

To run all linters and tests before you commit new code, call the `preMerge` task.
```bash
gradle preMerge --continue
```

Convenient way to resolve most of the issues found by *Ktlint* is to call the `ktlintFormat` task in the subproject you want.
```bash
# automatically format the code of the plugin implementation
gradle :plugin-build:plugin:ktlintFormat
```

## GitHub CI

In the [.github/workflows/](.github/workflows/) folder you can find scripts for the GitHub CI. The checking ones are being executed on each pull-request or push to main.

* The most important one is the [`checks.yaml`](.github/workflows/checks.yaml): it installs the necessary dependencies, runs the linters and implemented tests, and, finally, executes several checks to make sure the plugin actually provides the tasks in the example project.
* The [`gradle-wrapper-validation.yaml`](.github/workflows/gradle-wrapper-validation.yml) also runs on each pull-request: it simply checks that the gradle wrapper has a valid checksum.
* The [`publish-plugin.yaml`](.github/workflows/publish-plugin.yaml) one automatically publishes the plugin whenever new tag is pushed. It requires some environment set-up, check the publish plugin section for the details (TODO).

## Future plans

In this section you can find the tasks waiting to be done in order to make the plugin more powerful and well-maintained. If you'd like to solve some of them, we'll appreciate your help 🤍

* Write actual tests for the plugin. So far [the test folder](plugin-build/plugin/src/test/java/org/jetbrains/bitcodetools/plugin/) contains only a mock one.
* Support `–no-std-lib` option. The basic implemention is just to ignore the `kfun:kotlin.*` functions.
* Optimize the `linePatterns` search. Current Python implementation takes some noticeable time when executed on the huge projects due to regexes being used too straightforwardly.
* Build the complete graph of the functions calls. That will allow to implement the following features.
  * Implement the recursive extraction of the functions being called, but in the other direction: traversing through ancestors (the functions that call the functions that call target function etc...).
  * Get rid of the code from the standard library more efficiently: not only `kfun:kotlin.*` functions can be ignored, but also the ones that are reachable only from them.
  * Output the needed part of the graph to the user. It may be helpful to analyze the dependencies quickly. The best way is to make it interactive (for example, via html): so the click on the element redirects to its description.
* Support modes of verbosity. For example, currently the python script provides more logging at the DEBUG level, but there is no way to set it from the plugin so far.
* Create a Gradle task that checks that all necessary dependencies for the projects are properly set.
* Find the way to show the bitcode of the requested piece of the source code. That will make possible to analyze bitcode interactively (the same way as decompilation to the Java bytecode works).
  * Implement this tooling as IntelliJ IDEA / VSCode plugins, so as to make possible to analyze the bitcode together with the source code in the most convenient manner.
* Extend the plugin with the tools for the assembly language analysis. Some optimizations tend to occur only at the compilation-to-the-object-files stage, so it could be useful for user to conveniently inspect them too.
