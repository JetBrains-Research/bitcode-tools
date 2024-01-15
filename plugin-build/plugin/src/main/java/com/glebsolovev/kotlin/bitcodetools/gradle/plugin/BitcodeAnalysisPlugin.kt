package com.glebsolovev.kotlin.bitcodetools.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.kotlin.dsl.*
import java.io.File
import java.nio.file.Files.createDirectories

abstract class BitcodeAnalysisPlugin : Plugin<Project> {

    companion object {
        private const val DECOMPILE_BITCODE_EXTENSION_NAME = "decompileBitcodeConfig"
        private const val PIPELINE_DECOMPILE_BITCODE_TASK_NAME = "decompileBitcode"
        private const val STANDALONE_DECOMPILE_BITCODE_TASK_NAME = "decompileSomeBitcode"

        private const val EXTRACT_BITCODE_EXTENSION_NAME = "extractFromDecompiledBitcodeConfig"
        private const val PIPELINE_EXTRACT_BITCODE_TASK_NAME = "extractBitcode"
        private const val STANDALONE_EXTRACT_BITCODE_TASK_NAME = "extractSomeBitcode"

        private val PIPELINE_BITCODE_TASK_NAMES = setOf(
            PIPELINE_DECOMPILE_BITCODE_TASK_NAME,
            PIPELINE_EXTRACT_BITCODE_TASK_NAME
        )

        private const val PIPELINE_VIOLATION_ERROR_MESSAGE =
            "`input` file of the `$PIPELINE_EXTRACT_BITCODE_TASK_NAME` should be an output file of the `$PIPELINE_DECOMPILE_BITCODE_TASK_NAME` task ${
            ""
            }to maintain the reasonable bitcode-analysis pipeline for the project. ${
            ""
            }If you still would like to overcome this behaviour, ${
            ""
            }register a custom `ExtractBitcodeTask` task and run it instead."

        const val GROUP_NAME = "bitcode analysis"
    }

    override fun apply(project: Project) {
        val (decompileBitcodeExtension, extractBitcodeExtension) = project.createExtensions()
        project.registerStandaloneBitcodeTasks()

        project.afterEvaluate {
            val resolvedBitcodeFiles = decompileBitcodeExtension.resolveFiles(project)
            registerPipelineDecompileBitcodeTask(
                decompileBitcodeTaskName = PIPELINE_DECOMPILE_BITCODE_TASK_NAME,
                linkTaskName = decompileBitcodeExtension.linkTaskName,
                resolvedBitcodeFiles = resolvedBitcodeFiles
            )
            registerPipelineExtractBitcodeTask(
                extractBitcodeTaskName = PIPELINE_EXTRACT_BITCODE_TASK_NAME,
                extractBitcodeExtension,
                resolvedBitcodeFiles
            )
            configureCompilerToProduceTmpFilesForPipeline(
                tmpArtifactsDirectory = resolvedBitcodeFiles.tmpArtifactsDirectory,
                setCompilerFlags = decompileBitcodeExtension.setCompilerFlags
            )
        }
    }

    private fun Project.createExtensions() = CreatedExtensions(
        decompileBitcodeExtension =
        extensions.create<DecompileBitcodeExtension>(DECOMPILE_BITCODE_EXTENSION_NAME, project),
        extractBitcodeExtension =
        extensions.create<ExtractBitcodeExtension>(EXTRACT_BITCODE_EXTENSION_NAME, project)
    )

    private fun Project.registerStandaloneBitcodeTasks() {
        tasks.register<DecompileBitcodeTask>(STANDALONE_DECOMPILE_BITCODE_TASK_NAME) {}
        tasks.register<ExtractBitcodeTask>(STANDALONE_EXTRACT_BITCODE_TASK_NAME) {}
    }

    private fun Project.registerPipelineDecompileBitcodeTask(
        decompileBitcodeTaskName: String,
        linkTaskName: String,
        resolvedBitcodeFiles: ResolvedBitcodeFiles,
    ) {
        val (tmpArtifactsDirectory, bcInputFilePath, llOutputFilePath) = resolvedBitcodeFiles
        val linkTask = tasks.named(linkTaskName)
        linkTask {
            outputs.dir(tmpArtifactsDirectory)
        }
        tasks.register<DecompileBitcodeTask>(decompileBitcodeTaskName) {
            dependsOn(linkTask)
            inputFilePath = bcInputFilePath
            outputFilePath = llOutputFilePath
        }
    }

    private fun Project.registerPipelineExtractBitcodeTask(
        extractBitcodeTaskName: String,
        extractBitcodeExtension: ExtractBitcodeExtension,
        resolvedBitcodeFiles: ResolvedBitcodeFiles,
    ) {
        val (tmpArtifactsDirectory, _, llOutputFilePath) = resolvedBitcodeFiles
        val extractedBitcodeOutputFilePath =
            resolveToRelativePath(tmpArtifactsDirectory, extractBitcodeExtension.outputFileName)

        tasks.register<ExtractBitcodeTask>(extractBitcodeTaskName) {
            dependsOn(PIPELINE_DECOMPILE_BITCODE_TASK_NAME)
            inputFilePath = llOutputFilePath
            outputFilePath = extractedBitcodeOutputFilePath
            functionToExtractName = extractBitcodeExtension.functionToExtractName
            recursionDepth = extractBitcodeExtension.recursionDepth
            doFirst {
                if (inputFilePath.orNull != llOutputFilePath) {
                    throw BitcodeAnalysisException(PIPELINE_VIOLATION_ERROR_MESSAGE)
                }
            }
        }
    }

    private fun Project.configureCompilerToProduceTmpFilesForPipeline(
        tmpArtifactsDirectory: Directory,
        setCompilerFlags: (compilerFlags: List<String>) -> Unit,
    ) {
        val shouldProduceBitcode = gradle.startParameter.taskNames.any { taskName ->
            PIPELINE_BITCODE_TASK_NAMES.any { taskName.contains(it) }
        }
        if (shouldProduceBitcode) {
            createDirectories(tmpArtifactsDirectory.asFile.toPath())
            val compilerFlags = listOf("-Xtemporary-files-dir=${tmpArtifactsDirectory.asFile.absolutePath}")
            setCompilerFlags(compilerFlags)
        }
    }

    private fun DecompileBitcodeExtension.resolveFiles(project: Project): ResolvedBitcodeFiles {
        val tmpArtifactsDirectory = project.layout.projectDirectory.dir(tmpArtifactsDirectoryPath)
        if (!bcInputFileName.validateExtension("bc")) {
            throw BitcodeAnalysisException("`bcInputFileName` should have `.bc` extension")
        }
        if (!llOutputFileName.validateExtension("ll")) {
            throw BitcodeAnalysisException("`llOutputFileName` should have `.ll` extension")
        }
        return ResolvedBitcodeFiles(
            tmpArtifactsDirectory,
            project.resolveToRelativePath(tmpArtifactsDirectory, bcInputFileName),
            project.resolveToRelativePath(tmpArtifactsDirectory, llOutputFileName)
        )
    }

    private fun String.validateExtension(expectedExt: String): Boolean {
        val actualExt = File(this).extension
        return actualExt == expectedExt
    }

    private fun Project.resolveToRelativePath(directory: Directory, fileName: String) =
        resolveToRelativePath(directory.file(fileName))

    private fun Project.resolveToRelativePath(file: RegularFile) = file.asFile.toRelativeString(projectDir)

    private data class CreatedExtensions(
        val decompileBitcodeExtension: DecompileBitcodeExtension,
        val extractBitcodeExtension: ExtractBitcodeExtension,
    )

    private data class ResolvedBitcodeFiles(
        val tmpArtifactsDirectory: Directory,
        val bcInputFilePath: String,
        val llOutputFilePath: String,
    )
}
