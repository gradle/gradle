/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.integtests.fixtures

import org.gradle.util.TestFile

class MavenRepository {
    private final TestFile rootDir

    MavenRepository(TestFile rootDir) {
        this.rootDir = rootDir
    }

    MavenModule module(String groupId, String artifactId, Object version = '1.0', String classifier = null) {
        def artifactDir = rootDir.file("${groupId.replace('.', '/')}/$artifactId/$version")
        return new MavenModule(artifactDir, groupId, artifactId, version as String, classifier)
    }
}

class MavenModule {
    final TestFile moduleDir
    final String groupId
    final String artifactId
    final String version
    private final List dependencies = []
    final String classifier
    int publishCount = 0

    MavenModule(TestFile moduleDir, String groupId, String artifactId, String version, String classifier = null) {
        this.moduleDir = moduleDir
        this.groupId = groupId
        this.artifactId = artifactId
        this.version = version
        this.classifier = classifier
    }

    MavenModule dependsOn(String dependencyArtifactId) {
        dependsOn(groupId, dependencyArtifactId, '1.0')
        return this
    }

    MavenModule dependsOn(String group, String artifactId, String version) {
        this.dependencies << [groupId: group, artifactId: artifactId, version: version]
        return this
    }

    void assertArtifactsDeployed(String... names) {
        for (name in names) {
            moduleDir.file(name).assertIsFile()
            moduleDir.file("${name}.md5").assertIsFile()
            moduleDir.file("${name}.sha1").assertIsFile()
        }
    }

    File getPomFile() {
        return new File(moduleDir, "$artifactId-${version}.pom")
    }


    TestFile getArtifactFile() {
        def fileName = "$artifactId-${version}.jar"
        if (classifier) {
            fileName = "$artifactId-$version-${classifier}.jar"
        }
        return moduleDir.file(fileName)
    }

    void publishWithChangedContent() {
        publishCount++
        publishArtifact()
    }

    File publishArtifact() {
        moduleDir.createDir()

        pomFile.text = ""
        pomFile << """
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>$groupId</groupId>
  <artifactId>$artifactId</artifactId>
  <packaging>jar</packaging>
  <version>$version</version>"""

        dependencies.each { dependency ->
            pomFile << """
  <dependencies>
    <dependency>
      <groupId>$dependency.groupId</groupId>
      <artifactId>$dependency.artifactId</artifactId>
      <version>$dependency.version</version>
    </dependency>
  </dependencies>"""
        }

        pomFile << "\n</project>"

        def jarFile = artifactFile
        jarFile << "add some content so that file size isn't zero: $publishCount"

        return jarFile
    }

}
