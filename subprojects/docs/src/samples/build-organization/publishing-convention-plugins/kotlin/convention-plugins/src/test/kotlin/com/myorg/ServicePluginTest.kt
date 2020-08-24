package com.myorg


import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServicePluginTest : PluginTest() {

    @Before
    fun init() {
        buildFile.appendText("""
            plugins {
                id("com.myorg.service-conventions")
            }
        """)
    }

    @Test
    fun `integrationTest and readmeCheck tasks run with check task`() {
        testProjectDir.newFile("README.md").writeText("""
            ## Service API

        """.trimIndent())

        val result = runTask("check")

        assertEquals(TaskOutcome.NO_SOURCE, result.task(":test")?.outcome)
        assertEquals(TaskOutcome.NO_SOURCE, result.task(":integrationTest")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":readmeCheck")?.outcome)
    }

    @Test
    fun `can use integrationTest configuration to define dependencies`() {
        buildFile.appendText("""
            dependencies {
                integrationTestImplementation("junit:junit:4.13")
            }
        """)

        testProjectDir.newFolder("src", "integrationTest", "java", "com", "myorg")
        testProjectDir.newFile("src/integrationTest/java/com/myorg/SomeIntegrationTest.java").writeText("""
            package com.myorg;

            import org.junit.Test;

            public class SomeIntegrationTest {
                @Test
                public void sampleTest() {
                }
            }
        """)

        val result = runTask ("integrationTest")

        assertEquals(TaskOutcome.SUCCESS, result.task(":integrationTest")?.outcome)
    }

    @Test
    fun `fails when no README exists`() {
        val result = runTaskWithFailure ("check")

        assertEquals(TaskOutcome.FAILED, result.task(":readmeCheck")?.outcome)
    }

    @Test
    fun `fails when README does not have service API section`() {
        testProjectDir.newFile("README.md").writeText("""
            asdfadfsasf
        """.trimIndent())

        val result = runTaskWithFailure ("check")

        assertEquals(TaskOutcome.FAILED, result.task(":readmeCheck")?.outcome)
        assertTrue(result.output.contains("README should contain section: ^## Service API$"))
    }
}
