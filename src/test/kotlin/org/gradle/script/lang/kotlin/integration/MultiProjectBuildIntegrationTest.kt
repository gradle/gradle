package org.gradle.script.lang.kotlin.integration

import org.gradle.script.lang.kotlin.integration.fixture.DeepThought

import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

class MultiProjectBuildIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `given a request for the classpath of a subproject, it will return the correct classpath`() {
        withFile("settings.gradle", """
            include 'foo', 'bar'
            rootProject.children.each {
                it.buildFileName = 'build.gradle.kts'
            }
        """)

        withClassJar("libs/fixture.jar", DeepThought::class.java)

        withFile("foo/build.gradle.kts", """
            buildscript {
                dependencies { classpath(files("../libs/fixture.jar")) }
            }
        """)
        withFile("bar/build.gradle.kts", """
            buildscript {}
        """)

        assertThat(
            kotlinBuildScriptModelCanonicalClassPathFor(existing("foo")),
            hasItem(existing("libs/fixture.jar")))

        assertThat(
            kotlinBuildScriptModelCanonicalClassPathFor(existing("bar")),
            not(hasItem(existing("libs/fixture.jar"))))
    }
}
