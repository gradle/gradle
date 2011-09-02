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

import java.security.MessageDigest
import java.text.SimpleDateFormat
import org.gradle.util.TestFile

/**
 * A fixture for dealing with Maven repositories.
 */
class MavenRepository {
    final TestFile rootDir

    MavenRepository(TestFile rootDir) {
        this.rootDir = rootDir
    }

    MavenModule module(String groupId, String artifactId, Object version = '1.0', String classifier = null, String type = 'jar') {
        def artifactDir = rootDir.file("${groupId.replace('.', '/')}/$artifactId/$version")
        return new MavenModule(artifactDir, groupId, artifactId, version as String, classifier, type)
    }
}

class MavenModule {
    final TestFile moduleDir
    final String groupId
    final String artifactId
    final String version
    final String type
    private final List dependencies = []
    final String classifier
    int publishCount = 1
    final updateFormat = new SimpleDateFormat("yyyyMMddHHmmss")
    final timestampFormat = new SimpleDateFormat("yyyyMMdd.HHmmss")

    MavenModule(TestFile moduleDir, String groupId, String artifactId, String version, String classifier = null, String type = 'jar') {
        this.moduleDir = moduleDir
        this.groupId = groupId
        this.artifactId = artifactId
        this.version = version
        this.classifier = classifier
        this.type = type
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
        def artifactNames = names
        if (version.endsWith('-SNAPSHOT')) {
            def metaData = new XmlParser().parse(moduleDir.file('maven-metadata.xml'))
            def timestamp = metaData.versioning.snapshot.timestamp[0].text().trim()
            def build = metaData.versioning.snapshot.buildNumber[0].text().trim()
            artifactNames = names.collect { it.replace('-SNAPSHOT', "-${timestamp}-${build}")}
        }
        for (name in artifactNames) {
            moduleDir.file(name).assertIsFile()
            moduleDir.file("${name}.md5").assertIsFile()
            moduleDir.file("${name}.sha1").assertIsFile()
        }
    }

    File getPomFile() {
        return new File(moduleDir, "$artifactId-${publishArtifactVersion}.pom")
    }


    TestFile getArtifactFile() {
        def fileName = "$artifactId-${publishArtifactVersion}.${type}"
        if (classifier) {
            fileName = "$artifactId-$publishArtifactVersion-${classifier}.${type}"
        }
        return moduleDir.file(fileName)
    }

    void publishWithChangedContent() {
        publishCount++
        publishArtifact()
    }

    String getPublishArtifactVersion() {
        return version.endsWith("-SNAPSHOT") ? "${version.replaceFirst('-SNAPSHOT$', '')}-${timestampFormat.format(publishTimestamp)}-${publishCount}" : version
    }

    Date getPublishTimestamp() {
        return new Date(updateFormat.parse("20100101120000").time + publishCount * 1000)
    }

    File publishArtifact() {
        moduleDir.createDir()

        if (version.endsWith("-SNAPSHOT")) {
            def metaDataFile = moduleDir.file('maven-metadata.xml')
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
            createHashFiles(metaDataFile)
        }

        pomFile.text = ""
        pomFile << """
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>$groupId</groupId>
  <artifactId>$artifactId</artifactId>
  <packaging>$type</packaging>
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

        createHashFiles(pomFile)

        return publishArtifactOnly()
    }

    File publishArtifactOnly() {
        def jarFile = artifactFile
        jarFile << "add some content so that file size isn't zero: $publishCount"

        createHashFiles(jarFile)

        return jarFile
    }

    private void createHashFiles(File file) {
        moduleDir.file("${file.name}.sha1").text = getHash(file, "SHA1")
        moduleDir.file("${file.name}.md5").text = getHash(file, "MD5")
    }

    private String getHash(File file, String algorithm) {
        MessageDigest messageDigest = MessageDigest.getInstance(algorithm)
        messageDigest.update(file.bytes)
        return new BigInteger(1, messageDigest.digest()).toString(16)
    }
}
