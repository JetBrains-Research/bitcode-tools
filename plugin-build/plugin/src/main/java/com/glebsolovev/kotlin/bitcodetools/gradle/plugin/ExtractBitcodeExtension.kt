package com.glebsolovev.kotlin.bitcodetools.gradle.plugin

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import javax.inject.Inject

/**
 * Gradle extension to set up the bitcode-analysis pipeline of the project:
 * namely, the `extractBitcode` & `extractBitcodeDebug` tasks.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class ExtractBitcodeExtension @Inject constructor(project: Project) {

    private val objects = project.objects

    /** Names of the functions to extract (should be specified exactly the same as in the bitcode file). */
    val functionNames: ListProperty<String> = objects.listProperty(String::class.java)

    /** Extract all functions with the names matching the specified regex patterns. */
    val functionPatterns: ListProperty<String> = objects.listProperty(String::class.java)

    /** Extract all functions that contain at least one code line matching the specified regex patterns. */
    val linePatterns: ListProperty<String> = objects.listProperty(String::class.java)

    /** Ignore all functions with the names matching the specified regex patterns. */
    val ignorePatterns: ListProperty<String> = objects.listProperty(String::class.java)

    /**
     * Enables recursive extraction of all called functions
     * up to the specified depth, relative to the target functions.
     * Default depth is `0`, meaning recursive extraction is disabled.
     */
    var recursionDepth: UInt = 0u

    /**
     * Enables logging: prints extra info messages to the console
     * to track the extraction process. It is disabled by default.
     */
    var verbose: Boolean = false

    /** Name of the file to save extracted bitcode into, `"extracted-bitcode.ll"` by default. */
    var outputFileName: String = "extracted-bitcode.ll"

    /** Name of the file to save extracted debug bitcode into, `"extracted-bitcode-debug.ll"` by default. */
    var debugOutputFileName: String = "extracted-bitcode-debug.ll"
}
