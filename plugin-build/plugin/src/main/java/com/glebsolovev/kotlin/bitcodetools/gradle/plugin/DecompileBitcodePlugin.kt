package com.glebsolovev.kotlin.bitcodetools.gradle.plugin

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.kotlin.dsl.*
import java.io.File
import java.nio.file.Files.createDirectories

abstract class DecompileBitcodePlugin : Plugin<Project> {

    companion object {
        private const val EXTENSION_NAME = "decompileBitcodeConfig"
        private const val TASK_NAME = "decompileBitcode"
    }

    override fun apply(project: Project) {
        val extension = project.extensions.create<DecompileBitcodeExtension>(EXTENSION_NAME, project)

        project.afterEvaluate {
            val resolvedFiles = extension.resolveFiles(project)
            configureDecompileBitcodeTask(
                project,
                decompileBitcodeTaskName = TASK_NAME,
                linkTaskName = extension.linkTaskName,
                resolvedFiles = resolvedFiles
            )
            configureCompilerToProduceTmpFiles(
                project,
                decompileBitcodeTaskNames = listOf(TASK_NAME),
                tmpArtifactsDirectory = resolvedFiles.tmpArtifactsDirectory,
                setCompilerFlags = extension.setCompilerFlags
            )
        }
    }

    private fun configureDecompileBitcodeTask(
        project: Project,
        decompileBitcodeTaskName: String,
        linkTaskName: String,
        resolvedFiles: ResolvedFiles,
    ) {
        val (tmpArtifactsDirectory, bcInputFilePath, llOutputFilePath) = resolvedFiles
        val linkTask = project.tasks.named(linkTaskName)
        linkTask {
            outputs.dir(tmpArtifactsDirectory)
        }
        project.tasks.register<DecompileBitcodeTask>(decompileBitcodeTaskName) {
            dependsOn(linkTask)
            inputFilePath.convention(bcInputFilePath)
            outputFilePath.convention(llOutputFilePath)
        }
    }

    private fun DecompileBitcodeExtension.resolveFiles(project: Project): ResolvedFiles {
        val tmpArtifactsDirectory = project.layout.projectDirectory.dir(tmpArtifactsDirectoryPath)
        if (!bcInputFileName.validateExtension("bc")) {
            throw GradleException("`bcInputFileName` should have `.bc` extension")
        }
        if (!llOutputFileName.validateExtension("ll")) {
            throw GradleException("`llOutputFileName` should have `.ll` extension")
        }
        return ResolvedFiles(
            tmpArtifactsDirectory,
            tmpArtifactsDirectory.file(bcInputFileName).toRelativePath(project),
            tmpArtifactsDirectory.file(llOutputFileName).toRelativePath(project)
        )
    }

    private fun configureCompilerToProduceTmpFiles(
        project: Project,
        decompileBitcodeTaskNames: List<String>,
        tmpArtifactsDirectory: Directory,
        setCompilerFlags: (compilerFlags: List<String>) -> Unit,
    ) {
        val shouldProduceBitcode = project.gradle.startParameter.taskNames.any { taskName ->
            decompileBitcodeTaskNames.any { taskName.contains(it) }
        }
        if (shouldProduceBitcode) {
            createDirectories(tmpArtifactsDirectory.asFile.toPath())
            val compilerFlags = listOf("-Xtemporary-files-dir=${tmpArtifactsDirectory.asFile.absolutePath}")
            setCompilerFlags(compilerFlags)
        }
    }

    private fun String.validateExtension(expectedExt: String): Boolean {
        val actualExt = File(this).extension
        return actualExt == expectedExt
    }

    private fun RegularFile.toRelativePath(project: Project) = asFile.toRelativeString(project.projectDir)

    private data class ResolvedFiles(
        val tmpArtifactsDirectory: Directory,
        val bcInputFilePath: String,
        val llOutputFilePath: String,
    )
}
