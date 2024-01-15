package com.glebsolovev.kotlin.bitcodetools.gradle.plugin

import org.gradle.api.Project
import javax.inject.Inject

@Suppress("UnnecessaryAbstractClass")
abstract class ExtractBitcodeExtension @Inject constructor(project: Project) {

    // required to initialize properties of type Property<*> correctly
    private val objects = project.objects

    var functionToExtractName: String? = null
    var recursionDepth: UInt = 0u
    var outputFileName: String = "extracted-bitcode.ll"
}