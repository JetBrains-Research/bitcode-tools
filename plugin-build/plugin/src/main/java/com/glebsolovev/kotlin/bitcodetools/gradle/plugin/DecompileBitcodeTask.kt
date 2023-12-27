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
        group = DecompileBitcodePlugin.GROUP_NAME
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
    val inputFile: RegularFileProperty = objects.fileProperty().value(
        project.layout.projectDirectory.file(inputFilePath)
    )

    @get:OutputFile
    val outputFile: RegularFileProperty = objects.fileProperty().value(
        project.layout.projectDirectory.file(outputFilePath)
    )

    @TaskAction
    fun produce() {
        val bcFile = inputFile.get().asFile
        val llFile = outputFile.get().asFile
        project.exec {
            executable = "sh"
            args = listOf("-c", "llvm-dis -o ${llFile.absolutePath} ${bcFile.absolutePath}")
        }
        logger.lifecycle("Bitcode has been successfully decompiled into ${llFile.absolutePath}.")
    }
}
