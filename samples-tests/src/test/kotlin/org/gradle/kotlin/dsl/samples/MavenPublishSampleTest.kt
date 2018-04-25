package org.gradle.kotlin.dsl.samples

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.jar.JarFile


class MavenPublishSampleTest : AbstractSampleTest("maven-publish") {

    @Test
    fun `publish`() {

        // given:
        val projectName = "maven-publish"
        val projectVersion = "1.0.0"
        val mavenPath = "org/gradle/sample/$projectName/$projectVersion"

        // when:
        build("publish")

        // then: repository exists
        val repoDir = existing("build/repo")

        // and: repository contains main JAR
        val mainJar = File(repoDir, "$mavenPath/$projectName-$projectVersion.jar")
        assertTrue("jar file $mainJar exists", mainJar.exists())

        // and: main JAR contains .class entries
        JarFile(mainJar).use {
            val numClassEntries = it.stream().filter { it.name.endsWith(".class") }.count()
            assertTrue(numClassEntries > 0)
        }

        // and: repository contains sources JAR
        val sourcesJar = File(repoDir, "$mavenPath/$projectName-$projectVersion-sources.jar")
        assertTrue("source file $sourcesJar exists", sourcesJar.exists())

        // and: sources JAR contains .java and .kt files
        JarFile(sourcesJar).use {
            assertTrue(it.stream().anyMatch { it.name.endsWith(".java") })
            assertTrue(it.stream().anyMatch { it.name.endsWith(".kt") })
        }
    }
}
