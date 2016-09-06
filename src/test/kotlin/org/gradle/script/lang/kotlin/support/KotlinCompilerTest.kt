package org.gradle.script.lang.kotlin.support

import org.gradle.script.lang.kotlin.TestWithTempFiles
import org.gradle.script.lang.kotlin.loggerFor

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File

import java.net.URLClassLoader

class KotlinCompilerTest : TestWithTempFiles() {

    @Test
    fun `can compile Kotlin source file into jar`() {

        val sourceFile = newFile("DeepThought.kt").apply {
            writeText("""
                package adams

                class DeepThought {
                    fun compute() = 42
                }
            """)
        }

        val outputJar = newFile("output.jar")

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

    private fun classLoaderFor(outputJar: File) =
        URLClassLoader.newInstance(
            arrayOf(outputJar.toURI().toURL()))
}
