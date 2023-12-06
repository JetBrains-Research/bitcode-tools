package com.glebsolovev.kotlin.bitcodetools.gradle.plugin

import org.gradle.api.Project
import javax.inject.Inject

@Suppress("UnnecessaryAbstractClass")
abstract class DecompileBitcodeExtension @Inject constructor(project: Project) {

    // required to initialize properties of type Property<*> correctly
    private val objects = project.objects

    // TODO: maybe use Property<*>?
    abstract var linkTaskName: String
    abstract var tmpArtifactsDirectoryPath: String
    abstract var setCompilerFlags: (compilerFlags: List<String>) -> Unit
}
