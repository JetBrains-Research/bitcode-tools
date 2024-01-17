package com.glebsolovev.kotlin.bitcodetools.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.writeText

abstract class ExtractBitcodeTask @Inject constructor(project: Project) : DefaultTask() {

    init {
        description = "Extracts the specified elements from a bitcode `.ll` file effectively."
        group = BitcodeAnalysisPlugin.GROUP_NAME
    }

    private val objects = project.objects

    companion object {
        private const val EXTRACT_BITCODE_SCRIPT_PATH = "/extract-bitcode.py"
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
        description = "Name of the function to extract (should be specified exactly the same as in the bitcode file). ${
        ""
        }Use this flag several times to specify multiple functions to extract, e.g. `--function foo --function bar`."
    )
    val functionsToExtract: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())

    @get:Input
    @get:Option(
        option = "recursionDepth",
        description =
        "Enables recursive extraction of all called functions ${
        ""
        }up to the specified depth relative to `functionToExtractName`. ${
        ""
        }Default depth is 0, meaning recursive extraction is disabled."
    )
    protected val actualRecursionDepthAsString: Property<String> =
        objects.property(String::class.java).convention(recursionDepth.toString())

    @get:Input
    @get:Option(
        option = "verbose",
        description = "Prints extra info messages to the console to track the extraction process."
    )
    val verbose: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    @get:InputFile
    val actualInputFile: RegularFileProperty = objects.fileProperty().value(
        project.layout.projectDirectory.file(inputFilePath)
    )

    @get:OutputFile
    val actualOutputFile: RegularFileProperty = objects.fileProperty().value(
        project.layout.projectDirectory.file(outputFilePath)
    )

    private fun validateArguments() {
        if (functionsToExtract.get().isEmpty()) {
            throw BitcodeAnalysisException("at least one function to extract should be specified")
        }
        if (actualRecursionDepthAsString.get().toUIntOrNull() == null) {
            throw BitcodeAnalysisException("`recursionDepth` must be a non-negative integer")
        }
    }

    private fun extractScriptIntoTmpFile(): Path {
        val scriptFileContent =
            object {}.javaClass.getResource(EXTRACT_BITCODE_SCRIPT_PATH)?.readText()
                ?: error("Failed to find and read `$EXTRACT_BITCODE_SCRIPT_PATH` resource")
        return kotlin.io.path.createTempFile(prefix = "extract-bitcode", suffix = ".py").apply {
            writeText(scriptFileContent)
        }
    }

    @TaskAction
    fun produce() {
        validateArguments()

        val scriptTmpFilePath = extractScriptIntoTmpFile().toAbsolutePath()
        val inputFilePath = actualInputFile.get().asFile.absolutePath
        val outputFilePath = actualOutputFile.get().asFile.absolutePath

        project.exec {
            executable = "sh"
            args = listOf(
                "-c",
                """
                    python3 "$scriptTmpFilePath" ${
                ""
                }--input "$inputFilePath" --output "$outputFilePath" ${
                ""
                }${functionsToExtract.get().joinToString(separator = " ") { "--function '$it'" }} ${
                ""
                }--recursive "${actualRecursionDepthAsString.get()}"${
                ""
                }${if (verbose.get()) " --verbose" else ""}
                """.trimIndent()
            )
        }
        logger.lifecycle("Specified elements have been successfully extracted into $outputFilePath.")
    }
}
