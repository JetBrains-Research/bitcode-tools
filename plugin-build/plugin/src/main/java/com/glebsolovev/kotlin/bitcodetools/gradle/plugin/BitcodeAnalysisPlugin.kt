package com.glebsolovev.kotlin.bitcodetools.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.execution.TaskExecutionGraphListener
import org.gradle.api.file.Directory
import org.gradle.kotlin.dsl.*
import java.nio.file.Files.createDirectories

abstract class BitcodeAnalysisPlugin : Plugin<Project> {

    companion object {
        private const val RELEASE_BITCODE_SOURCES_DIR_NAME = "releaseSources"
        private const val DEBUG_BITCODE_SOURCES_DIR_NAME = "debugSources"

        private const val DECOMPILE_BITCODE_EXTENSION_NAME = "decompileBitcodeConfig"
        private const val RELEASE_DECOMPILE_BITCODE_TASK_NAME = "decompileBitcode"
        private const val DEBUG_DECOMPILE_BITCODE_TASK_NAME = "decompileBitcodeDebug"
        private const val STANDALONE_DECOMPILE_BITCODE_TASK_NAME = "decompileSomeBitcode"

        private const val EXTRACT_BITCODE_EXTENSION_NAME = "extractFromDecompiledBitcodeConfig"
        private const val RELEASE_EXTRACT_BITCODE_TASK_NAME = "extractBitcode"
        private const val DEBUG_EXTRACT_BITCODE_TASK_NAME = "extractBitcodeDebug"
        private const val STANDALONE_EXTRACT_BITCODE_TASK_NAME = "extractSomeBitcode"

        private const val PIPELINE_VIOLATION_ERROR_MESSAGE =
            "`input` file of the `$RELEASE_EXTRACT_BITCODE_TASK_NAME` should be an output file of the `$RELEASE_DECOMPILE_BITCODE_TASK_NAME` task ${
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
            val tmpArtifactsDirectory = resolveToRelativeDirectory(decompileBitcodeExtension.tmpArtifactsDirectoryPath)
            listOf(
                resolveReleasePipelineParameters(
                    tmpArtifactsDirectory,
                    decompileBitcodeExtension,
                    extractBitcodeExtension
                ),
                resolveDebugPipelineParameters(
                    tmpArtifactsDirectory,
                    decompileBitcodeExtension,
                    extractBitcodeExtension
                )
            ).forEach {
                it.registerTasks(
                    project,
                    extractBitcodeTaskParameters = ExtractBitcodeTaskParameters(
                        functionToExtractName = extractBitcodeExtension.functionToExtractName,
                        recursionDepth = extractBitcodeExtension.recursionDepth,
                        verbose = extractBitcodeExtension.verbose
                    ),
                    setCompilerFlags = decompileBitcodeExtension.setCompilerFlags
                )
            }
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

    private fun BitcodeAnalysisPipelineParameters.registerTasks(
        project: Project,
        extractBitcodeTaskParameters: ExtractBitcodeTaskParameters,
        setCompilerFlags: (compilerFlags: List<String>) -> Unit,
    ) {
        registerDecompileBitcodeTask(project)
        registerExtractBitcodeTask(project, extractBitcodeTaskParameters)
        configureCompilerToProduceTmpFiles(project, setCompilerFlags)
    }

    private fun BitcodeAnalysisPipelineParameters.registerDecompileBitcodeTask(
        project: Project,
    ) {
        val linkTask = project.tasks.named(linkTaskName)
        linkTask {
            outputs.dir(bitcodeSourcesDirectory)
        }
        project.tasks.register<DecompileBitcodeTask>(decompileBitcodeTaskName) {
            dependsOn(linkTask)
            inputFilePath = bcFilePath
            outputFilePath = llFilePath
        }
    }

    private fun BitcodeAnalysisPipelineParameters.registerExtractBitcodeTask(
        project: Project,
        taskParameters: ExtractBitcodeTaskParameters,
    ) {
        project.tasks.register<ExtractBitcodeTask>(extractBitcodeTaskName) {
            dependsOn(decompileBitcodeTaskName)
            inputFilePath = llFilePath
            outputFilePath = extractedBitcodeFilePath
            functionToExtractName = taskParameters.functionToExtractName
            recursionDepth = taskParameters.recursionDepth
            verbose = taskParameters.verbose
            doFirst {
                if (inputFilePath.orNull != llFilePath) {
                    throw BitcodeAnalysisException(PIPELINE_VIOLATION_ERROR_MESSAGE)
                }
            }
        }
    }

    private fun BitcodeAnalysisPipelineParameters.configureCompilerToProduceTmpFiles(
        project: Project,
        setCompilerFlags: (compilerFlags: List<String>) -> Unit,
    ) {
        val pipelineTaskPaths = pipelineTaskNames.map { taskName ->
            project.tasks.findByPath(taskName)?.path
                ?: error("pipeline tasks should be already registered at this point")
        }
        project.gradle.addListener(object : TaskExecutionGraphListener {
            override fun graphPopulated(graph: TaskExecutionGraph) {
                val shouldProduceBitcode = pipelineTaskPaths.any { graph.hasTask(it) }
                if (shouldProduceBitcode) {
                    createDirectories(bitcodeSourcesDirectory.asFile.toPath())
                    val compilerFlags = listOf("-Xtemporary-files-dir=${bitcodeSourcesDirectory.asFile.absolutePath}")
                    setCompilerFlags(compilerFlags)
                }
            }
        })
    }

    private fun Project.resolveReleasePipelineParameters(
        tmpArtifactsDirectory: Directory,
        decompileBitcodeExtension: DecompileBitcodeExtension,
        extractBitcodeExtension: ExtractBitcodeExtension,
    ): BitcodeAnalysisPipelineParameters {
        val bitcodeSourcesDirectory = tmpArtifactsDirectory.dir(RELEASE_BITCODE_SOURCES_DIR_NAME)
        return BitcodeAnalysisPipelineParameters(
            linkTaskName = decompileBitcodeExtension.linkTaskName,
            decompileBitcodeTaskName = RELEASE_DECOMPILE_BITCODE_TASK_NAME,
            extractBitcodeTaskName = RELEASE_EXTRACT_BITCODE_TASK_NAME,
            bitcodeSourcesDirectory = bitcodeSourcesDirectory,
            bcFilePath = resolveToRelativePath(bitcodeSourcesDirectory, decompileBitcodeExtension.bcInputFileName),
            llFilePath = resolveToRelativePath(tmpArtifactsDirectory, decompileBitcodeExtension.llOutputFileName),
            extractedBitcodeFilePath = resolveToRelativePath(
                tmpArtifactsDirectory,
                extractBitcodeExtension.outputFileName
            )
        )
    }

    private fun Project.resolveDebugPipelineParameters(
        tmpArtifactsDirectory: Directory,
        decompileBitcodeExtension: DecompileBitcodeExtension,
        extractBitcodeExtension: ExtractBitcodeExtension,
    ): BitcodeAnalysisPipelineParameters {
        val bitcodeSourcesDirectory = tmpArtifactsDirectory.dir(DEBUG_BITCODE_SOURCES_DIR_NAME)
        return BitcodeAnalysisPipelineParameters(
            linkTaskName = decompileBitcodeExtension.linkDebugTaskName
                ?: error("debug pipeline can be registered only if `linkDebugTaskName` is set"),
            decompileBitcodeTaskName = DEBUG_DECOMPILE_BITCODE_TASK_NAME,
            extractBitcodeTaskName = DEBUG_EXTRACT_BITCODE_TASK_NAME,
            bitcodeSourcesDirectory = bitcodeSourcesDirectory,
            bcFilePath = resolveToRelativePath(bitcodeSourcesDirectory, decompileBitcodeExtension.bcInputFileName),
            llFilePath = resolveToRelativePath(tmpArtifactsDirectory, decompileBitcodeExtension.llDebugOutputFileName),
            extractedBitcodeFilePath = resolveToRelativePath(
                tmpArtifactsDirectory,
                extractBitcodeExtension.debugOutputFileName
            )
        )
    }

    private fun Project.resolveToRelativeDirectory(directoryPath: String) =
        layout.projectDirectory.dir(directoryPath)

    private fun Project.resolveToRelativePath(directory: Directory, fileName: String) =
        directory.file(fileName).asFile.toRelativeString(projectDir)

    private data class CreatedExtensions(
        val decompileBitcodeExtension: DecompileBitcodeExtension,
        val extractBitcodeExtension: ExtractBitcodeExtension,
    )

    private data class BitcodeAnalysisPipelineParameters(
        val linkTaskName: String,
        val decompileBitcodeTaskName: String,
        val extractBitcodeTaskName: String,
        val bitcodeSourcesDirectory: Directory,
        val bcFilePath: String,
        val llFilePath: String,
        val extractedBitcodeFilePath: String,
    ) {
        val pipelineTaskNames = setOf(decompileBitcodeTaskName, extractBitcodeTaskName)
    }

    private data class ExtractBitcodeTaskParameters(
        val functionToExtractName: String,
        val recursionDepth: UInt,
        val verbose: Boolean,
    )
}
