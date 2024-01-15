package com.glebsolovev.kotlin.bitcodetools.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import javax.inject.Inject

abstract class DecompileBitcodeTask @Inject constructor(project: Project) : DefaultTask() {

    init {
        description = "Decompiles a bitcode `.bc` file into a human-readable `.ll` one."
        group = BitcodeAnalysisPlugin.GROUP_NAME
    }

    private val objects = project.objects

    @get:Internal
    @get:Option(
        option = "input",
        description = "Path (relative to root) to the input bitcode `.bc` file."
    )
    val inputFilePath: Property<String> = objects.property(String::class.java)

    @get:Internal
    @get:Option(
        option = "output",
        description = "Path (relative to root) to the output human-readable `.ll` file."
    )
    val outputFilePath: Property<String> = objects.property(String::class.java)

    @get:InputFile
    val actualInputFile: RegularFileProperty = objects.fileProperty().value(
        project.layout.projectDirectory.file(inputFilePath)
    )

    @get:OutputFile
    val actualOutputFile: RegularFileProperty = objects.fileProperty().value(
        project.layout.projectDirectory.file(outputFilePath)
    )

    @TaskAction
    fun produce() {
        val bcFilePath = actualInputFile.get().asFile.absolutePath
        val llFilePath = actualOutputFile.get().asFile.absolutePath
        project.exec {
            executable = "sh"
            args = listOf("-c", "llvm-dis -o $llFilePath $bcFilePath")
        }
        logger.lifecycle("Bitcode has been successfully decompiled into $llFilePath.")
    }
}
