package org.gradle.kotlin.dsl.support

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.classLoaderFor

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

class KotlinCompilerTest : TestWithTempFiles() {

    @Test
    fun `can compile Kotlin source file into jar`() {

        val sourceFile =
            newFile("DeepThought.kt", """
                package hhgttg

                class DeepThought {
                    fun compute(): Int = 42
                }
            """)

        val outputJar = newFile("output.jar")
        compileToJar(outputJar, listOf(sourceFile), loggerFor<KotlinCompilerTest>())

        val answer =
            classLoaderFor(outputJar).use { it
                .loadClass("hhgttg.DeepThought")
                .newInstance()
                .run {
                    this::class.java.getMethod("compute").invoke(this)
                }
            }

        assertThat(
            answer,
            equalTo<Any>(42))

        assert(outputJar.delete())
    }
}

