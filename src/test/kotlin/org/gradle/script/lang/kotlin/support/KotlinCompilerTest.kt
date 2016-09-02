package org.gradle.script.lang.kotlin.support

import org.gradle.script.lang.kotlin.loggerFor

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import java.io.File

import java.net.URLClassLoader

class KotlinCompilerTest {

    @JvmField
    @Rule val tempDir = TemporaryFolder()

    @Test
    fun `can compile Kotlin source file into jar`() {

        val sourceFile = tempFile("DeepThought.kt").apply {
            writeText("""
                package adams

                class DeepThought {
                    fun compute() = 42
                }
            """)
        }

        val outputJar = tempFile("output.jar")

        compileToJar(outputJar, sourceFile, loggerFor<KotlinCompilerTest>())

        val answer =
            classLoaderFor(outputJar)
                .loadClass("adams.DeepThought")
                .newInstance()
                .run {
                    javaClass.getMethod("compute").invoke(this)
                }
        assertThat(
            answer,
            equalTo<Any>(42))
    }

    private fun tempFile(fileName: String) =
        tempDir.newFile(fileName)

    private fun classLoaderFor(outputJar: File) =
        URLClassLoader.newInstance(
            arrayOf(outputJar.toURI().toURL()))
}
