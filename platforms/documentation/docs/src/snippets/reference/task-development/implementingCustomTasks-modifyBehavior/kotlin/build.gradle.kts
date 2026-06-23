plugins {
    id("java")
}

// tag::modify-behavior[]
tasks.compileJava {
    // Modify the task behavior
    doLast {
        val outputDir = File("$buildDir/compiledClasses")
        outputDir.mkdirs()

        val compiledFiles = sourceSets["main"].output.files
        compiledFiles.forEach { compiledFile ->
            val destinationFile = File(outputDir, compiledFile.name)
            compiledFile.copyTo(destinationFile, true)
        }

        println("Java compilation completed. Compiled classes copied to: ${outputDir.absolutePath}")
    }
}
// end::modify-behavior[]
