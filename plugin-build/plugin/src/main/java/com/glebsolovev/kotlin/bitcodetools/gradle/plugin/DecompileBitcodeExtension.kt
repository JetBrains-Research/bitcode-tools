package com.glebsolovev.kotlin.bitcodetools.gradle.plugin

import org.gradle.api.Project
import javax.inject.Inject

abstract class DecompileBitcodeExtension @Inject constructor(project: Project) {

    @Suppress("unused") // required to initialize properties of type Property<*> correctly
    private val objects = project.objects

    abstract var linkTaskName: String
    abstract var setCompilerFlags: (compilerFlags: List<String>) -> Unit

    var artifactsDirectoryPath: String = "build/bitcode"
    var bcInputFileName: String = "out.bc"
    var llOutputFileName: String = "bitcode.ll"

    var linkDebugTaskName: String? = null
    var llDebugOutputFileName: String = "bitcode-debug.ll"
}
