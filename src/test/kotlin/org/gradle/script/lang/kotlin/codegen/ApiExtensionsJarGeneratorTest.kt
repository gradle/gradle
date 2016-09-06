package org.gradle.script.lang.kotlin.codegen

import org.gradle.script.lang.kotlin.TestWithTempFiles

import org.gradle.script.lang.kotlin.support.classEntriesFor
import org.gradle.script.lang.kotlin.support.zipTo

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test
import java.io.File

import java.util.jar.JarFile

class ApiExtensionsJarGeneratorTest : TestWithTempFiles() {

    @Test
    fun `includes source with documentation taken from ActionExtensions resource`() {

        val inputFile = apiJarWith(org.gradle.api.Project::class.java)
        val outputFile = newFile("extensions.jar")

        ApiExtensionsJarGenerator(NullCompiler).generate(outputFile, inputFile)

        assertThat(
            textForEntryOf(outputFile, "org/gradle/script/lang/kotlin/ActionExtensions.kt"),
            containsString(
                firstLinesOf(
                    kdocFor("org.gradle.api.Project.allprojects(org.gradle.api.Project.() -> Unit)"))))
    }

    private fun firstLinesOf(text: String) =
        // using only the first 4 lines for comparison to avoid
        // the additional notice automatically added to each method
        text.lineSequence().take(4).joinToString(separator = "\n")

    object NullCompiler : KotlinFileCompiler {
        override fun compileToDirectory(outputDirectory: File, sourceFile: File, classPath: List<File>) = Unit
    }

    private fun apiJarWith(vararg classes: Class<*>): File {
        val inputFile = newFile("api.jar")
        zipTo(inputFile, classEntriesFor(*classes))
        return inputFile
    }

    private fun textForEntryOf(outputFile: File, entryName: String): String =
        JarFile(outputFile).use { outputJar ->
            val sourceFileEntry = outputJar.getEntry(entryName)
            outputJar.getInputStream(sourceFileEntry).bufferedReader().readText()
        }

    private fun kdocFor(signature: String): String =
        MarkdownKDocProvider
            .fromResource("/doc/ActionExtensions.md")
            .invoke(signature)!!
            .format()
}
