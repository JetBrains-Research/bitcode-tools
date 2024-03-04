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

/** Gradle task that extracts the specified elements from a bitcode `.ll` file effectively. */
abstract class ExtractBitcodeTask @Inject constructor(project: Project) : DefaultTask() {

    init {
        description = "Extracts the specified elements from a bitcode `.ll` file effectively."
        group = BitcodeAnalysisPlugin.GROUP_NAME
    }

    private val objects = project.objects

    companion object {
        private const val EXTRACT_BITCODE_SCRIPT_PATH = "/extract-bitcode.py"
    }

    /** Path (relative to the project's root) to the input `.ll` file. */
    @get:Internal
    @get:Option(
        option = "input",
        description = "Path (relative to the project's root) to the input `.ll` file."
    )
    val inputFilePath: Property<String> = objects.property(String::class.java)

    /** Path (relative to the project's root) to the output `.ll` file with the extracted bitcode. */
    @get:Internal
    @get:Option(
        option = "output",
        description = "Path (relative to the project's root) to the output `.ll` file with the extracted bitcode."
    )
    val outputFilePath: Property<String> = objects.property(String::class.java)

    /**
     * Enables recursive extraction of all called functions
     * up to the specified depth, relative to the target functions.
     * Default depth is `0`, meaning recursive extraction is disabled.
     */
    @get:Internal
    var recursionDepth: UInt = 0u

    /** Names of the functions to extract (should be specified exactly the same as in the bitcode file). */
    @get:Input
    @get:Option(
        option = "function",
        description = "Name of the function to extract (should be specified exactly the same as in the bitcode file). ${
        ""
        }Use this flag several times to specify multiple functions to extract, e.g. `--function foo --function bar`."
    )
    val functionNames: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())

    /** Extract all functions with the names matching the specified regex patterns. */
    @get:Input
    @get:Option(
        option = "function-pattern",
        description = "Extract all functions with the names matching the specified regex pattern. ${
        ""
        }Use this flag several times to provide multiple patterns to search for, ${
        ""
        }e.g. `--function-pattern foo --function-pattern bar`."
    )
    val functionPatterns: ListProperty<String> =
        objects.listProperty(String::class.java).convention(emptyList())

    /** Extract all functions that contain at least one code line matching the specified regex patterns. */
    @get:Input
    @get:Option(
        option = "line-pattern",
        description = "Extract all functions that contain at least one code line ${
        ""
        }matching the specified regex pattern. ${
        ""
        }Use this flag several times to provide multiple patterns to search for, ${
        ""
        }e.g. `--line-pattern foo --line-pattern bar`."
    )
    val linePatterns: ListProperty<String> =
        objects.listProperty(String::class.java).convention(emptyList())

    /** Ignore all functions with the names matching the specified regex patterns. */
    @get:Input
    @get:Option(
        option = "ignore-function-pattern",
        description = "Ignore all functions with the names matching the specified regex pattern. ${
        ""
        }Use this flag several times to provide multiple patterns to ignore, ${
        ""
        }e.g. `--ignore-function-pattern foo --ignore-function-pattern bar`."
    )
    val ignorePatterns: ListProperty<String> =
        objects.listProperty(String::class.java).convention(emptyList())

    /**
     * Property that is used to support [recursionDepth] parameter in the command line.
     * It uses [recursionDepth] value by default properly,
     * so it is not recommended to modify this property. */
    @get:Input
    @get:Option(
        option = "recursion-depth",
        description =
        "Enables recursive extraction of all called functions ${
        ""
        }up to the specified depth relative to the target functions. ${
        ""
        }Default depth is 0, meaning recursive extraction is disabled."
    )
    protected val actualRecursionDepthAsString: Property<String> =
        objects.property(String::class.java).convention(
            project.provider {
                recursionDepth.toString()
            }
        )

    /**
     * Enables logging: prints extra info messages to the console
     * to track the extraction process. It is disabled by default.
     */
    @get:Input
    @get:Option(
        option = "verbose",
        description = "Prints extra info messages to the console to track the extraction process."
    )
    val verbose: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    /**
     * By default, stores [inputFilePath] resolved to the project's directory.
     * This property is expected to be readonly.
     */
    @get:InputFile
    val actualInputFile: RegularFileProperty = objects.fileProperty().value(
        project.layout.projectDirectory.file(inputFilePath)
    )

    /**
     * By default, stores [outputFilePath] resolved to the project's directory.
     * This property is expected to be readonly.
     */
    @get:OutputFile
    val actualOutputFile: RegularFileProperty = objects.fileProperty().value(
        project.layout.projectDirectory.file(outputFilePath)
    )

    private fun validateArguments() {
        if (functionNames.get().isEmpty() && functionPatterns.get().isEmpty() && linePatterns.get().isEmpty()) {
            throw BitcodeAnalysisException(
                "at least one function to extract by its name, pattern or a line pattern should be specified"
            )
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

    private fun ListProperty<String>.toPythonFlags(flagName: String) =
        get().run {
            if (isEmpty()) {
                ""
            } else {
                joinToString(separator = " ", postfix = " ") { "$flagName '$it'" }
            }
        }

    /** Performs bitcode extraction. */
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
                }${functionNames.toPythonFlags("--function")}${
                ""
                }${functionPatterns.toPythonFlags("--function-pattern")}${
                ""
                }${linePatterns.toPythonFlags("--line-pattern")}${
                ""
                }${ignorePatterns.toPythonFlags("--ignore-function-pattern")}${
                ""
                }--recursion-depth "${actualRecursionDepthAsString.get()}"${
                ""
                }${if (verbose.get()) " --verbose" else ""}
                """.trimIndent()
            )
        }
        logger.lifecycle("Specified elements have been successfully extracted into $outputFilePath.")
    }
}
