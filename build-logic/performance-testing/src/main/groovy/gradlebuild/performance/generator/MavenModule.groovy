/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.performance.generator

import java.text.SimpleDateFormat

class MavenModule {
    final File moduleDir
    final String groupId
    final String artifactId
    final String version
    String parentPomSection
    String type = 'jar'
    private final List dependencies = []
    int publishCount = 1
    final updateFormat = new SimpleDateFormat("yyyyMMddHHmmss")
    final timestampFormat = new SimpleDateFormat("yyyyMMdd.HHmmss")
    private final List artifacts = []
    private boolean uniqueSnapshots = true;
    MavenJarCreator mavenJarCreator

    MavenModule(File moduleDir, String groupId, String artifactId, String version) {
        this.moduleDir = moduleDir
        this.groupId = groupId
        this.artifactId = artifactId
        this.version = version
    }

    MavenModule dependsOn(String dependencyArtifactId) {
        dependsOn(groupId, dependencyArtifactId, '1.0')
        return this
    }

    MavenModule dependsOn(String group, String artifactId, String version) {
        this.dependencies << [groupId: group, artifactId: artifactId, version: version]
        return this
    }

    String shortNotation() {
        return "$groupId:$artifactId:$version"
    }

    File getPomFile() {
        return new File(moduleDir, "$artifactId-${publishArtifactVersion}.pom")
    }

    File artifactFile(Map<String, ?> options) {
        def artifact = toArtifact(options)
        def fileName = "$artifactId-${publishArtifactVersion}.${artifact.type}"
        if (artifact.classifier) {
            fileName = "$artifactId-$publishArtifactVersion-${artifact.classifier}.${artifact.type}"
        }
        return new File(moduleDir, fileName)
    }

    String getPublishArtifactVersion() {
        if (uniqueSnapshots && version.endsWith("-SNAPSHOT")) {
            return "${version.replaceFirst('-SNAPSHOT$', '')}-${timestampFormat.format(publishTimestamp)}-${publishCount}"
        }
        return version
    }

    Date getPublishTimestamp() {
        return new Date(updateFormat.parse("20100101120000").time + publishCount * 1000)
    }

    /**
     * Publishes the pom.xml plus main artifact, plus any additional artifacts for this module.
     */
    MavenModule publish() {
        moduleDir.mkdirs()

        if (uniqueSnapshots && version.endsWith("-SNAPSHOT")) {
            def metaDataFile = new File(moduleDir, 'maven-metadata.xml')
            metaDataFile.text = """
<metadata>
  <groupId>$groupId</groupId>
  <artifactId>$artifactId</artifactId>
  <version>$version</version>
  <versioning>
    <snapshot>
      <timestamp>${timestampFormat.format(publishTimestamp)}</timestamp>
      <buildNumber>$publishCount</buildNumber>
    </snapshot>
    <lastUpdated>${updateFormat.format(publishTimestamp)}</lastUpdated>
  </versioning>
</metadata>
"""
        }

        pomFile.text = ""
        pomFile << """
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>$groupId</groupId>
  <artifactId>$artifactId</artifactId>
  <packaging>$type</packaging>
  <version>$version</version>
  <description>Published on $publishTimestamp</description>
  <dependencies>"""

        if (parentPomSection) {
            pomFile << "\n$parentPomSection\n"
        }

        dependencies.each { dependency ->
            pomFile << """
    <dependency>
      <groupId>$dependency.groupId</groupId>
      <artifactId>$dependency.artifactId</artifactId>
      <version>$dependency.version</version>
    </dependency>"""
        }

        pomFile << """
  </dependencies>
</project>"""

        artifacts.each { artifact ->
            publishArtifact(artifact)
        }
        publishArtifact([:])
        return this
    }

    void createEmptyJar(File artifactFile) {
        mavenJarCreator.createJar(this, artifactFile)
    }

    private File publishArtifact(Map<String, ?> artifact) {
        def artifactFile = artifactFile(artifact)
        if (type != 'pom') {
            if (type == 'jar') {
                createEmptyJar(artifactFile)
            } else {
                artifactFile << "add some content so that file size isn't zero: $publishCount"
            }
        }

        return artifactFile
    }

    private Map<String, Object> toArtifact(Map<String, ?> options) {
        options = new HashMap<String, Object>(options)
        def artifact = [type: options.remove('type') ?: type, classifier: options.remove('classifier') ?: null]
        assert options.isEmpty(): "Unknown options : ${options.keySet()}"
        return artifact
    }
}
