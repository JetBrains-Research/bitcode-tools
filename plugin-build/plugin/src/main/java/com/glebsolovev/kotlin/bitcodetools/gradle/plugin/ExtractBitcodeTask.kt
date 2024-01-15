package com.glebsolovev.kotlin.bitcodetools.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import javax.inject.Inject
import kotlin.io.path.writeText

abstract class ExtractBitcodeTask @Inject constructor(project: Project) : DefaultTask() {

    init {
        description = "Extracts the specified elements from a bitcode `.ll` file effectively."
        group = DecompileBitcodePlugin.GROUP_NAME
    }

    private val objects = project.objects

    companion object {
        private const val EXTRACT_BITCODE_SCRIPT_PATH = "/extract-bitcode.py"
        private const val NO_FUNCTIONS_TO_EXTRACT_ERROR_MESSAGE = "No functions to extract!\n${
            ""
        }Please, provide their names via the CLI `function` parameter or ${
            ""
        }`functionToExtractName` field during Gradle configuration."
    }

    @get:Internal
    @get:Option(
        option = "input",
        description = "Path (relative to root) to the input `.ll` file."
    )
    val inputFilePath: Property<String> = objects.property(String::class.java)

    @get:Internal
    @get:Option(
        option = "output",
        description = "Path (relative to root) to the output `.ll` file with the extracted bitcode."
    )
    val outputFilePath: Property<String> = objects.property(String::class.java)

    @get:Internal
    var recursionDepth: UInt = 0u

    @get:Input
    @get:Option(
        option = "function",
        description = "Name of the function to extract (should be specified exactly the same as in the bitcode file)."
    )
    val functionToExtractName: Property<String> = objects.property(String::class.java)

    @get:Input
    @get:Option(
        option = "recursionDepth",
        description =
        "Enables recursive extraction of all called functions " +
            "up to the specified depth relative to `functionToExtractName`. " +
            "Default depth is 0, meaning recursive extraction is disabled."
    )
    protected val actualRecursionDepthAsString: Property<String> =
        objects.property(String::class.java).convention(recursionDepth.toString())

    @get:InputFile
    val inputFile: RegularFileProperty = objects.fileProperty().value(
        project.layout.projectDirectory.file(inputFilePath)
    )

    @get:OutputFile
    val outputFile: RegularFileProperty = objects.fileProperty().value(
        project.layout.projectDirectory.file(outputFilePath)
    )

    @TaskAction
    fun produce() {
        if (!functionToExtractName.isPresent) {
            logger.lifecycle(NO_FUNCTIONS_TO_EXTRACT_ERROR_MESSAGE)
            return
        }

        val scriptFileContent =
            object {}.javaClass.getResource(EXTRACT_BITCODE_SCRIPT_PATH)?.readText() ?: error("unexpected")

        val scriptTmpFile = kotlin.io.path.createTempFile(prefix = "extract-bitcode", suffix = ".py")
        scriptTmpFile.writeText(scriptFileContent)

        val inputFilePath = inputFile.get().asFile.absolutePath
        val outputFilePath = outputFile.get().asFile.absolutePath
        // TODO: check arguments (?)
        // TODO: quote bash args
        project.exec {
            executable = "sh"
            args = listOf(
                "-c",
                """
                    python3 -i "${scriptTmpFile.toAbsolutePath()}" ${
                    ""
                }--input $inputFilePath --output $outputFilePath ${
                    ""
                }--function '${functionToExtractName.get()}' ${
                    ""
                }--recursive ${actualRecursionDepthAsString.get()}
                """.trimIndent()
            )
        }
        // TODO: handle errors from script
        logger.lifecycle("Specified elements have been successfully extracted into $outputFilePath.")
    }
}
