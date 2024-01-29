package com.glebsolovev.kotlin.bitcodetools.gradle.plugin

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import javax.inject.Inject

@Suppress("UnnecessaryAbstractClass")
abstract class ExtractBitcodeExtension @Inject constructor(project: Project) {

    private val objects = project.objects

    val functionsToExtractNames: ListProperty<String> = objects.listProperty(String::class.java)
    val functionsToExtractPatterns: ListProperty<String> = objects.listProperty(String::class.java)
    val functionsToIgnorePatterns: ListProperty<String> = objects.listProperty(String::class.java)

    var recursionDepth: UInt = 0u
    var verbose: Boolean = false

    var outputFileName: String = "extracted-bitcode.ll"
    var debugOutputFileName: String = "extracted-bitcode-debug.ll"
}
