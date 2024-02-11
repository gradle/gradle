package com.myorg

import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class LibraryPluginTest : PluginTest() {

    @Before
    fun init() {
        buildFile.appendText("""
            plugins {
                id("com.myorg.library-conventions")
            }
        """)
    }

    @Test
    fun `can declare api dependencies`() {
        readmeContainingMandatorySectionsExists()
        buildFile.appendText("""
            dependencies {
                api("org.apache.commons:commons-lang3:3.4")
            }
        """)

        val result = runTask("build")

        assertEquals(TaskOutcome.SUCCESS, result.task(":build")?.outcome)
    }

    @Test
    fun `publishes library with versionin`() {
        readmeContainingMandatorySectionsExists()
        settingsFile.writeText("""
            rootProject.name = "my-library"
        """.trimIndent())
        buildFile.appendText("""
            version = "0.1.0"

            publishing {
                repositories {
                    maven {
                        name = "testRepo"
                        url = uri("build/test-repo")
                    }
                }
            }
        """)

        testProjectDir.newFolder("src", "main", "java", "com", "myorg")
        testProjectDir.newFile("src/main/java/com/myorg/Util.java").writeText("""
            package com.myorg;

            public class Util {
                public static void someUtil() {
                }
            }
        """)

        val result = runTask("publishLibraryPublicationToTestRepoRepository")

        assertEquals(TaskOutcome.SUCCESS, result.task(":jar")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":publishLibraryPublicationToTestRepoRepository")?.outcome)
        assertTrue(File(testProjectDir.getRoot(), "build/test-repo/com/myorg/my-library/0.1.0/my-library-0.1.0.jar").exists())
    }

    @Test
    fun `fails when no README exists`() {
        val result = runTaskWithFailure ("check")

        assertEquals(TaskOutcome.FAILED, result.task(":readmeCheck")?.outcome)
    }

    @Test
    fun `fails when README does not have API section`() {
        testProjectDir.newFile("README.md").writeText("""
            ## Changelog
            - change 1
            - change 2
        """.trimIndent())

        val result = runTaskWithFailure ("check")

        assertEquals(TaskOutcome.FAILED, result.task(":readmeCheck")?.outcome)
        assertTrue(result.output.contains("README should contain section: ^## API$"))
    }

    @Test
    fun `fails when README does not have Changelog section`() {
        testProjectDir.newFile("README.md").writeText("""
            ## API
            public API description
        """.trimIndent())

        val result = runTaskWithFailure ("check")

        assertEquals(TaskOutcome.FAILED, result.task(":readmeCheck")?.outcome)
        assertTrue(result.output.contains("README should contain section: ^## Changelog$"))
    }

    private fun readmeContainingMandatorySectionsExists() {
        testProjectDir.newFile("README.md").writeText("""
            ## API
            public API description

            ## Changelog
            - change 1
            - change 2
        """.trimIndent())
    }
}
