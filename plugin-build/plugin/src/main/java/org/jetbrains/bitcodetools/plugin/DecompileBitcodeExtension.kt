package org.jetbrains.bitcodetools.plugin

import org.gradle.api.Project
import javax.inject.Inject

/**
 * Gradle extension to set up the bitcode-analysis pipeline of the project:
 * namely, general parameters and the `decompileBitcode` & `decompileBitcodeDebug` tasks.
 */
abstract class DecompileBitcodeExtension @Inject constructor(project: Project) {

    @Suppress("unused") // required to initialize properties of type Property<*> correctly
    private val objects = project.objects

    /** Name of the Gradle task to link the project. */
    abstract var linkTaskName: String

    /**
     * Function that specifies how flags are passed
     * to the Kotlin/Native compiler used in build.
     */
    abstract var setCompilerFlags: (compilerFlags: List<String>) -> Unit

    /**
     * Path to the directory to store all the input and output bitcode artifacts
     * (relative to the project directory). It is set to `"build/bitcode"` by default.
     */
    var artifactsDirectoryPath: String = "build/bitcode"

    /**
     * Name of the `*.bc` file produced by
     * the [linkTaskName] and [linkDebugTaskName] tasks, `"out.bc"` by default.
     */
    var bcInputFileName: String = "out.bc"

    /** Name of the `*.ll` file to decompile bitcode into, `"bitcode.ll"` by default. */
    var llOutputFileName: String = "bitcode.ll"

    /** Name of the Gradle task to link the project in the debug mode. It is not set by default. */
    var linkDebugTaskName: String? = null

    /** Name of the `*.ll` file to decompile debug bitcode into, `"bitcode-debug.ll"` by default. */
    var llDebugOutputFileName: String = "bitcode-debug.ll"
}
