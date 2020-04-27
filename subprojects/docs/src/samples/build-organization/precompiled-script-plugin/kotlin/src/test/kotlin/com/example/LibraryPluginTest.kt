package com.example

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
                id("com.example.library")
            }
        """)
    }

    @Test
    fun `can declare api dependencies`() {
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

        testProjectDir.newFolder("src", "main", "java", "com", "example")
        testProjectDir.newFile("src/main/java/com/example/Util.java").writeText("""
            package com.example;

            public class Util {
                public static void someUtil() {
                }
            }
        """)

        val result = runTask("publishLibraryPublicationToTestRepoRepository")

        assertEquals(TaskOutcome.SUCCESS, result.task(":jar")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":publishLibraryPublicationToTestRepoRepository")?.outcome)
        assertTrue(File(testProjectDir.getRoot(), "build/test-repo/com/example/my-library/0.1.0/my-library-0.1.0.jar").exists())
    }
}
