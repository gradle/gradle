package org.gradle.kotlin.dsl.samples

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths
import java.util.jar.JarFile

class MavenPublishSampleTest : AbstractSampleTest("maven-publish") {
    @Test
    fun `publish`() {
        val projectName = "maven-publish"
        val projectVersion = "1.0.0"

        // when:
        build("publish")

        // then:
        val repo = projectRoot.toPath().resolve("build").resolve("repo")
        val mavenPath = repo.resolve(Paths.get("org", "gradle", "sample", projectName, projectVersion))

        // repo contains default jar file
        mavenPath.resolve("$projectName-$projectVersion.jar").toFile().let { jarFile ->
            assertTrue("jar file $jarFile exists", jarFile.exists())

            // jar file contains .class entries
            JarFile(jarFile).use {
                val numClassEntries = it.stream().filter { it.name.endsWith(".class") }.count()
                assertTrue(numClassEntries > 0)
            }
        }

        // repo contains source file
        mavenPath.resolve("$projectName-$projectVersion-sources.jar").toFile().let { sourceFile ->
            assertTrue("source file $sourceFile exists", sourceFile.exists())

            // source file contains java and kt files
            JarFile(sourceFile).use {
                assertTrue(it.stream().anyMatch { it.name.endsWith(".java") })
                assertTrue(it.stream().anyMatch { it.name.endsWith(".kt") })
            }
        }
    }
}
