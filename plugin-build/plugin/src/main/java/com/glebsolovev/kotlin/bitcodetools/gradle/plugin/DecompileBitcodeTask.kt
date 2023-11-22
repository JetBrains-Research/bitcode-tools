package com.glebsolovev.kotlin.bitcodetools.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class DecompileBitcodeTask : DefaultTask() {

    init {
        description = "Decompiles a bitcode `.bc` file into a human-readable `.ll` one."
        group = "bitcode analysis"
    }

    // TODO: add @Option and @Optional
    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun produce() {
        val bcFile = inputFile.get().asFile
        val llFile = outputFile.get().asFile
        project.exec {
            executable = "sh"
            args = listOf("-c", "llvm-dis -o ${llFile.absolutePath} ${bcFile.absolutePath}")
        }
        logger.lifecycle("Bitcode has been successfully decompiled into ${bcFile.absolutePath}.")
    }
}
