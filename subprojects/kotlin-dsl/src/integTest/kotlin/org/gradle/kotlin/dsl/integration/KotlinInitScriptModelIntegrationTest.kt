package org.gradle.kotlin.dsl.integration

import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File


class KotlinInitScriptModelIntegrationTest : ScriptModelIntegrationTest() {

    @Test
    fun `initscript classpath does not include buildSrc`() {

        withBuildSrc()
        withDefaultSettings()

        val initScript = withFile("my.init.gradle.kts")
        val classPath = canonicalClassPathFor(initScript)

        assertContainsGradleKotlinDslJars(classPath)
        assertThat(
            classPath.map { it.name },
            not(hasBuildSrc()))
    }

    @Test
    fun `can fetch initscript classpath in face of compilation errors`() {

        withDefaultSettings()
        withFile("classes.jar")

        val initScript =
            withFile("my.init.gradle.kts", """
                initscript {
                    dependencies {
                        classpath(files("classes.jar"))
                    }
                }

                val p =
            """)

        val classPath = canonicalClassPathFor(initScript)

        assertContainsGradleKotlinDslJars(classPath)
        assertClassPathContains(
            classPath,
            existing("classes.jar"))
    }

    private
    fun canonicalClassPathFor(initScript: File) =
        canonicalClassPathFor(projectRoot, initScript)
}
