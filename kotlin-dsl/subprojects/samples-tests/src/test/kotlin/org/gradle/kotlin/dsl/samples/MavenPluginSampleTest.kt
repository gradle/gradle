package org.gradle.kotlin.dsl.samples

import org.junit.Test
import org.junit.Assert.assertThat

import org.xmlunit.matchers.CompareMatcher.isIdenticalTo
import java.io.File


class MavenPluginSampleTest : AbstractSampleTest("maven-plugin") {

    @Test
    fun `uploadArchives publishes custom pom`() {
        build("uploadArchives", "-Dmaven.repo.local=$tempMavenLocalDir")
        assertPom("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <modelVersion>4.0.0</modelVersion>
              <parent>
                <groupId>org.gradle</groupId>
                <artifactId>kotlin-dsl</artifactId>
                <version>1.0</version>
              </parent>
              <groupId>org.gradle.kotlin-dsl</groupId>
              <artifactId>$projectName</artifactId>
              <version>1.0</version>
              <licenses>
                <license>
                  <name>The Apache Software License, Version 2.0</name>
                  <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
                  <distribution>repo</distribution>
                </license>
              </licenses>
            </project>
        """)
    }

    private
    fun assertPom(expectedPom: String) =
        assertThat(pomFile(), isIdenticalTo(expectedPom.trim()).ignoreWhitespace())

    private
    fun pomFile(): File =
        existing("build/m2/releases/org/gradle/kotlin-dsl/$projectName/1.0/$projectName-1.0.pom")

    private
    val projectName by lazy {
        projectRoot.name
    }

    private
    val tempMavenLocalDir by lazy {
        existing("build/m2/local")
    }
}
