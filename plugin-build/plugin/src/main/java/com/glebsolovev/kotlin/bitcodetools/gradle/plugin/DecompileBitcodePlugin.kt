package com.glebsolovev.kotlin.bitcodetools.gradle.plugin

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.kotlin.dsl.*
import java.io.File
import java.nio.file.Files.createDirectories

// TODO: rename to BitcodeAnalysisPlugin
abstract class DecompileBitcodePlugin : Plugin<Project> {

    companion object {
        private const val DECOMPILE_BITCODE_EXTENSION_NAME = "decompileBitcodeConfig"
        private const val EXTRACT_BITCODE_EXTENSION_NAME = "extractFromDecompiledBitcodeConfig"
        private const val DECOMPILE_BITCODE_TASK_NAME = "decompileBitcode"
        private const val EXTRACT_BITCODE_TASK_NAME = "extractBitcode"
        const val GROUP_NAME = "bitcode analysis"
    }

    override fun apply(project: Project) {
        val decompileBitcodeExtension = project.extensions.create<DecompileBitcodeExtension>(DECOMPILE_BITCODE_EXTENSION_NAME, project)
        val extractBitcodeExtension = project.extensions.create<ExtractBitcodeExtension>(EXTRACT_BITCODE_EXTENSION_NAME, project)

        project.afterEvaluate {
            val resolvedFiles = decompileBitcodeExtension.resolveFiles(project)
            configureDecompileBitcodeTask(
                project,
                decompileBitcodeTaskName = DECOMPILE_BITCODE_TASK_NAME,
                linkTaskName = decompileBitcodeExtension.linkTaskName,
                resolvedFiles = resolvedFiles
            )
            configureCompilerToProduceTmpFiles(
                project,
                decompileBitcodeTaskNames = listOf(DECOMPILE_BITCODE_TASK_NAME),
                tmpArtifactsDirectory = resolvedFiles.tmpArtifactsDirectory,
                setCompilerFlags = decompileBitcodeExtension.setCompilerFlags
            )

            // NEW DRAFT BLOCK
            // TODO: add if extension is set block
            val (tmpArtifactsDirectory, _, llOutputFilePath) = resolvedFiles
            val extractedFile = tmpArtifactsDirectory.file(extractBitcodeExtension.outputFileName).toRelativePath(project)
            project.tasks.register<ExtractBitcodeTask>(EXTRACT_BITCODE_TASK_NAME) {
                dependsOn(DECOMPILE_BITCODE_TASK_NAME)
                inputFilePath.convention(llOutputFilePath)
                outputFilePath.convention(extractedFile)
                functionToExtractName.set(extractBitcodeExtension.functionToExtractName)
                recursionDepth.set(extractBitcodeExtension.recursionDepth.toString())
            }
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
