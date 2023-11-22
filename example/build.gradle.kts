plugins {
    java
    id("com.glebsolovev.kotlin.bitcodetools.gradle.plugin")
}

// TODO: move small full-fledged K/N project here as a better one example 

decompileBitcodeConfig {
    linkTaskName = "assemble"
    tmpArtifactsDirectoryPath = "build/tmpArtifacts"
    setCompilerFlags = { compilerFlags ->
        println("[example] compiler flags are set: $compilerFlags")
    }
}
