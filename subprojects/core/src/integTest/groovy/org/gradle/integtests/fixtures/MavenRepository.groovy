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

    MavenModule module(String groupId, String artifactId, Object version = '1.0') {
        def artifactDir = rootDir.file("$groupId/$artifactId/$version")
        return new MavenModule(artifactDir, groupId, artifactId, version as String)
    }
}

class MavenModule {
    final TestFile moduleDir
    final String groupId
    final String artifactId
    final String version
    private final List<String> dependencies = []

    MavenModule(TestFile moduleDir, String groupId, String artifactId, String version) {
        this.moduleDir = moduleDir
        this.groupId = groupId
        this.artifactId = artifactId
        this.version = version
    }

    MavenModule dependsOn(String dependencyArtifactId) {
        this.dependencies << dependencyArtifactId
        return this
    }

    void assertArtifactsDeployed(String... names) {
        for (name in names) {
            moduleDir.file(name).assertIsFile()
            moduleDir.file("${name}.md5").assertIsFile()
            moduleDir.file("${name}.sha1").assertIsFile()
        }
    }

    File publishArtifact() {
        moduleDir.createDir()

        def pomFile = new File(moduleDir, "$artifactId-${version}.pom")
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
      <groupId>$groupId</groupId>
      <artifactId>$dependency</artifactId>
      <version>1.0</version>
    </dependency>
  </dependencies>"""
        }

        pomFile << "\n</project>"

        def jarFile = new File("$moduleDir/$artifactId-${version}.jar")
        jarFile << "add some content so that file size isn't zero"

        return jarFile
    }

}
