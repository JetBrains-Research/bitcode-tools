package com.glebsolovev.kotlin.bitcodetools.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import java.nio.file.Files.createDirectories
import java.io.File
import org.gradle.api.GradleException

const val EXTENSION_NAME = "decompileBitcodeConfig"
const val TASK_NAME = "decompileBitcode"

private fun String.validateExtension(expectedExt: String): Boolean {
    val actualExt = File(this).extension
    return actualExt == expectedExt
}

abstract class DecompileBitcodePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<DecompileBitcodeExtension>(EXTENSION_NAME, project)

        // TODO: refactor and split into functions
        project.afterEvaluate {
            val linkTask = project.tasks.named(extension.linkTaskName)
            val tmpArtifactsDirectory = project.layout.projectDirectory.dir(extension.tmpArtifactsDirectoryPath)

            val bcInputFileName = extension.bcInputFileName
            if(!bcInputFileName.validateExtension("bc")) {
                throw GradleException("`bcInputFileName` should have `.bc` extension")
            }
            val llOutputFileName = extension.llOutputFileName
            if(!llOutputFileName.validateExtension("ll")) {
                throw GradleException("`llOutputFileName` should have `.ll` extension")
            }

            linkTask {
                outputs.dir(tmpArtifactsDirectory)
            }
            project.tasks.register<DecompileBitcodeTask>(TASK_NAME) {
                dependsOn(linkTask)
                inputFile.set(tmpArtifactsDirectory.file(bcInputFileName))
                outputFile.set(tmpArtifactsDirectory.file(llOutputFileName))
            }

            // configure compiler so that it produces temporary artifacts
            val shouldProduceBitcode = gradle.startParameter.taskNames.any { it.contains("decompileBitcode") }
            if (shouldProduceBitcode) {
                createDirectories(tmpArtifactsDirectory.asFile.toPath())
                val compilerFlags = listOf("-Xtemporary-files-dir=${tmpArtifactsDirectory.asFile.absolutePath}")
                extension.setCompilerFlags(compilerFlags)
            }
        }
    }
}
