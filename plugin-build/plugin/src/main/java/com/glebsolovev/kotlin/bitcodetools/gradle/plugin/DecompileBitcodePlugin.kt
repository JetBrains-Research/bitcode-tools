package com.glebsolovev.kotlin.bitcodetools.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import java.nio.file.Files.createDirectories

const val EXTENSION_NAME = "decompileBitcodeConfig"
const val TASK_NAME = "decompileBitcode"

abstract class DecompileBitcodePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(EXTENSION_NAME, DecompileBitcodeExtension::class.java, project)
        
        // TODO: refactor and split into functions
        project.afterEvaluate {
            val linkTask = project.tasks.named(extension.linkTaskName)
            val tmpArtifactsDirectory = project.layout.projectDirectory.dir(extension.tmpArtifactsDirectoryPath)
            
            linkTask {
                outputs.dir(tmpArtifactsDirectory)
                // TODO?: check whether it overrides original outputs or just adds a new one
                // in the former (worst) case a new task with dependance on `linkTask` should be created
            }
            // project.tasks.register<DecompileBitcodeTask>("decompileBitcode") {
            project.tasks.register(TASK_NAME, DecompileBitcodeTask::class.java) {
                dependsOn(linkTask)
                // TODO: support file names setup in the extension
                inputFile.set(tmpArtifactsDirectory.file("out.bc"))
                outputFile.set(tmpArtifactsDirectory.file("bitcode.ll"))
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
