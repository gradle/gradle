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
import junit.framework.AssertionFailedError
import org.gradle.util.TestFile

/**
 * A fixture for dealing with Maven repositories.
 */
class MavenRepository {
    final TestFile rootDir

    MavenRepository(TestFile rootDir) {
        this.rootDir = rootDir
    }

    URI getUri() {
        return rootDir.toURI()
    }

    MavenModule module(String groupId, String artifactId, Object version = '1.0') {
        def artifactDir = rootDir.file("${groupId.replace('.', '/')}/$artifactId/$version")
        return new MavenModule(artifactDir, groupId, artifactId, version as String)
    }
}

class MavenModule {
    final TestFile moduleDir
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

    MavenModule(TestFile moduleDir, String groupId, String artifactId, String version) {
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

    /**
     * Specifies the type of the main artifact.
     */
    MavenModule hasType(String type) {
        this.type = type
        return this
    }

    /**
     * Adds an additional artifact to this module.
     * @param options Can specify any of: type or classifier
     */
    MavenModule artifact(Map<String, ?> options) {
        artifacts << options
        return this
    }

    MavenModule withNonUniqueSnapshots() {
        uniqueSnapshots = false;
        return this;
    }

    /**
     * Asserts that exactly the given artifacts have been deployed, along with their checksum files
     */
    void assertArtifactsPublished(String... names) {
        def artifactNames = names
        if (uniqueSnapshots && version.endsWith('-SNAPSHOT')) {
            def metaData = new XmlParser().parse(moduleDir.file('maven-metadata.xml'))
            def timestamp = metaData.versioning.snapshot.timestamp[0].text().trim()
            def build = metaData.versioning.snapshot.buildNumber[0].text().trim()
            artifactNames = names.collect { it.replace('-SNAPSHOT', "-${timestamp}-${build}")}
            artifactNames.add("maven-metadata.xml")
        }
        Set actual = moduleDir.list() as Set
        for (name in artifactNames) {
            assert actual.remove(name)
            assert actual.remove("${name}.md5" as String)
            assert actual.remove("${name}.sha1" as String)
        }
        assert actual.isEmpty()
    }

    MavenPom getPom() {
        return new MavenPom(pomFile)
    }

    TestFile getPomFile() {
        return moduleDir.file("$artifactId-${publishArtifactVersion}.pom")
    }

    TestFile getArtifactFile() {
        return artifactFile([:])
    }

    TestFile artifactFile(Map<String, ?> options) {
        def artifact = toArtifact(options)
        def fileName = "$artifactId-${publishArtifactVersion}.${artifact.type}"
        if (artifact.classifier) {
            fileName = "$artifactId-$publishArtifactVersion-${artifact.classifier}.${artifact.type}"
        }
        return moduleDir.file(fileName)
    }

    /**
     * Publishes the pom.xml plus main artifact, plus any additional artifacts for this module, with changed content to any
     * previous publication.
     */
    MavenModule publishWithChangedContent() {
        publishCount++
        return publish()
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
        moduleDir.createDir()

        if (uniqueSnapshots && version.endsWith("-SNAPSHOT")) {
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

        if (parentPomSection) {
           pomFile << "\n$parentPomSection\n"
        }

        dependencies.each { dependency ->
            pomFile << """
  <dependencies>
    <dependency>
      <groupId>$dependency.groupId</groupId>
      <artifactId>$dependency.artifactId</artifactId>
      <version>$dependency.version</version>
    </dependency>3.2.1
  </dependencies>"""
        }

        pomFile << "\n</project>"

        createHashFiles(pomFile)

        artifacts.each { artifact ->
            publishArtifact(artifact)
        }
        publishArtifact([:])
        return this
    }

    private File publishArtifact(Map<String, ?> artifact) {
        def artifactFile = artifactFile(artifact)
        if (type != 'pom') {
            artifactFile << "add some content so that file size isn't zero: $publishCount"
        }
        createHashFiles(artifactFile)
        return artifactFile
    }

    private Map<String, Object> toArtifact(Map<String, ?> options) {
        options = new HashMap<String, Object>(options)
        def artifact = [type: options.remove('type') ?: type, classifier: options.remove('classifier') ?: null]
        assert options.isEmpty() : "Unknown options : ${options.keySet()}"
        return artifact
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

class MavenPom {
    final Map<String, MavenScope> scopes = [:]

    MavenPom(File pomFile) {
        def pom = new XmlParser().parse(pomFile)
        pom.dependencies.dependency.each { dep ->
            def scopeElement = dep.scope
            def scopeName = scopeElement ? scopeElement.text() : "runtime"
            def scope = scopes[scopeName]
            if (!scope) {
                scope = new MavenScope()
                scopes[scopeName] = scope
            }
            scope.addDependency(dep.groupId.text(), dep.artifactId.text(), dep.version.text())
        }
    }

}

class MavenScope {
    final dependencies = []

    void addDependency(String groupId, String artifactId, String version) {
        dependencies << [groupId: groupId, artifactId: artifactId, version: version]
    }

    void assertDependsOnArtifacts(String... artifactIds) {
        assert dependencies.collect { it.artifactId} as Set == artifactIds as Set
    }

    void assertDependsOn(String groupId, String artifactId, String version) {
        def dep = [groupId: groupId, artifactId: artifactId, version: version]
        if (!dependencies.find { it == dep }) {
            throw new AssertionFailedError("Could not find expected dependency $dep. Actual: $dependencies")
        }
    }
}