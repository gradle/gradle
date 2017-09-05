package org.gradle.kotlin.dsl.samples

import org.junit.Assert
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

        // repo contains jar file
        val jarFile = mavenPath.resolve("$projectName-$projectVersion.jar").toFile()
        Assert.assertTrue("jar file $jarFile exists", jarFile.exists())

        // and jar file contains .class entries
        val numClassEntries = JarFile(jarFile).stream().filter { it.name.endsWith(".class") }.count()
        Assert.assertTrue(numClassEntries > 0)
    }
}
