/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.test.fixtures.maven

import groovy.xml.MarkupBuilder
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.hash.HashUtil

import java.text.SimpleDateFormat

class MavenFileModule implements MavenModule {
    final TestFile moduleDir
    final String groupId
    final String artifactId
    final String version
    private String parentPomSection
    String type = 'jar'
    String packaging
    private final List dependencies = []
    int publishCount = 1
    final updateFormat = new SimpleDateFormat("yyyyMMddHHmmss")
    final timestampFormat = new SimpleDateFormat("yyyyMMdd.HHmmss")
    private final List artifacts = []
    private boolean uniqueSnapshots = true;

    MavenFileModule(TestFile moduleDir, String groupId, String artifactId, String version) {
        this.moduleDir = moduleDir
        this.groupId = groupId
        this.artifactId = artifactId
        this.version = version
    }

    MavenFileModule parent(String group, String artifactId, String version) {
        parentPomSection = """
<parent>
  <groupId>${group}</groupId>
  <artifactId>${artifactId}</artifactId>
  <version>${version}</version>
</parent>
"""
        return this
    }

    MavenFileModule dependsOn(String... dependencyArtifactIds) {
        for (String id : dependencyArtifactIds) {
            dependsOn(groupId, id, '1.0')
        }
        return this
    }

    MavenFileModule dependsOn(String group, String artifactId, String version, String type = null) {
        this.dependencies << [groupId: group, artifactId: artifactId, version: version, type: type]
        return this
    }

    MavenFileModule hasPackaging(String packaging) {
        this.packaging = packaging
        return this
    }

    /**
     * Specifies the type of the main artifact.
     */
    MavenFileModule hasType(String type) {
        this.type = type
        return this
    }

    /**
     * Adds an additional artifact to this module.
     * @param options Can specify any of: type or classifier
     */
    MavenFileModule artifact(Map<String, ?> options) {
        artifacts << options
        return this
    }

    MavenFileModule withNonUniqueSnapshots() {
        uniqueSnapshots = false;
        return this;
    }

    void assertNotPublished() {
        pomFile.assertDoesNotExist()
    }

    void assertPublished() {
        assert pomFile.assertExists()
        assert parsedPom.groupId == groupId
        assert parsedPom.artifactId == artifactId
        assert parsedPom.version == version
    }

    void assertPublishedAsPomModule() {
        assertPublished()
        assertArtifactsPublished("${artifactId}-${version}.pom")
        assert parsedPom.packaging == "pom"
    }

    void assertPublishedAsJavaModule() {
        assertPublished()
        assertArtifactsPublished("${artifactId}-${version}.jar", "${artifactId}-${version}.pom")
        assert parsedPom.packaging == null
    }

    void assertPublishedAsWebModule() {
        assertPublished()
        assertArtifactsPublished("${artifactId}-${version}.war", "${artifactId}-${version}.pom")
        assert parsedPom.packaging == 'war'
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
        assert moduleDir.isDirectory()
        Set actual = moduleDir.list() as Set
        for (name in artifactNames) {
            assert actual.remove(name)
            assert actual.remove("${name}.md5" as String)
            assert actual.remove("${name}.sha1" as String)
        }
        assert actual.isEmpty()
    }

    MavenPom getParsedPom() {
        return new MavenPom(pomFile)
    }

    DefaultMavenMetaData getRootMetaData() {
        new DefaultMavenMetaData(rootMetaDataFile)
    }

    TestFile getPomFile() {
        return moduleDir.file("$artifactId-${publishArtifactVersion}.pom")
    }

    TestFile getMetaDataFile() {
        moduleDir.file("maven-metadata.xml")
    }

    TestFile getRootMetaDataFile() {
        moduleDir.parentFile.file("maven-metadata.xml")
    }

    TestFile getArtifactFile(Map options = [:]) {
        if (version.endsWith("-SNAPSHOT") && !metaDataFile.exists() && uniqueSnapshots) {
            def artifact = toArtifact(options)
            return moduleDir.file("${artifactId}-${version}${artifact.classifier ? "-${artifact.classifier}" : ""}.${artifact.type}")
        }
        return artifactFile(options)
    }

    TestFile getArtifactSha1File() {
        return getSha1File(artifactFile)
    }

    TestFile getArtifactMd5File() {
        return getMd5File(artifactFile)
    }

    TestFile artifactFile(Map<String, ?> options) {
        def artifact = toArtifact(options)
        def fileName = "$artifactId-${publishArtifactVersion}.${artifact.type}"
        if (artifact.classifier) {
            fileName = "$artifactId-$publishArtifactVersion-${artifact.classifier}.${artifact.type}"
        }
        return moduleDir.file(fileName)
    }

    MavenFileModule publishWithChangedContent() {
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

    MavenModule publish() {
        moduleDir.createDir()
        def rootMavenMetaData = getRootMetaDataFile()

        updateRootMavenMetaData(rootMavenMetaData)
        if (uniqueSnapshots && version.endsWith("-SNAPSHOT")) {
            def metaDataFile = moduleDir.file('maven-metadata.xml')
            publish(metaDataFile) {
                metaDataFile.text = """
<metadata>
  <!-- $publishCount -->
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
        }

        publish(pomFile) {
            def pomPackaging = packaging ?: type;
            pomFile.text = ""
            pomFile << """
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>$groupId</groupId>
  <artifactId>$artifactId</artifactId>
  <packaging>$pomPackaging</packaging>
  <version>$version</version>
  <description>Published on $publishTimestamp</description>"""

            if (parentPomSection) {
                pomFile << "\n$parentPomSection\n"
            }

            if (!dependencies.empty) {
                pomFile << """
  <dependencies>"""
            }

            dependencies.each { dependency ->
                def typeAttribute = dependency['type'] == null ? "" : "<type>$dependency.type</type>"
                pomFile << """
    <dependency>
      <groupId>$dependency.groupId</groupId>
      <artifactId>$dependency.artifactId</artifactId>
      <version>$dependency.version</version>
      $typeAttribute
    </dependency>"""
            }

            if (!dependencies.empty) {
                pomFile << """
  </dependencies>"""
            }

            pomFile << "\n</project>"
        }

        artifacts.each { artifact ->
            publishArtifact(artifact)
        }
        publishArtifact([:])
        return this
    }

    private void updateRootMavenMetaData(TestFile rootMavenMetaData) {
        def allVersions = rootMavenMetaData.exists() ? new XmlParser().parseText(rootMavenMetaData.text).versioning.versions.version*.value().flatten() : []
        allVersions << version;
        publish(rootMavenMetaData) {
            rootMavenMetaData.withWriter {writer ->
                def builder = new MarkupBuilder(writer)
                builder.metadata {
                    groupId(groupId)
                    artifactId(artifactId)
                    version(allVersions.max())
                    versioning {
                        if (uniqueSnapshots && version.endsWith("-SNAPSHOT")) {
                            snapshot {
                                timestamp(timestampFormat.format(publishTimestamp))
                                buildNumber(publishCount)
                                lastUpdated(updateFormat.format(publishTimestamp))
                            }
                        } else {
                            versions {
                                allVersions.each {currVersion ->
                                    version(currVersion)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private File publishArtifact(Map<String, ?> artifact) {
        def artifactFile = artifactFile(artifact)
        publish(artifactFile) {
            if (type != 'pom') {
                artifactFile.text = "${artifactFile.name} : $publishCount"
            }
        }
        return artifactFile
    }

    private publish(File file, Closure cl) {
        def lastModifiedTime = file.exists() ? file.lastModified() : null
        cl.call(file)
        if (lastModifiedTime != null) {
            file.setLastModified(lastModifiedTime + 2000)
        }
        createHashFiles(file)
    }

    private Map<String, Object> toArtifact(Map<String, ?> options) {
        options = new HashMap<String, Object>(options)
        def artifact = [type: options.remove('type') ?: type, classifier: options.remove('classifier') ?: null]
        assert options.isEmpty(): "Unknown options : ${options.keySet()}"
        return artifact
    }

    private void createHashFiles(File file) {
        sha1File(file)
        md5File(file)
    }

    TestFile getSha1File(File file) {
        getHashFile(file, "sha1")
    }

    TestFile sha1File(File file) {
        hashFile(file, "sha1");
    }

    TestFile getMd5File(File file) {
        getHashFile(file, "md5")
    }

    TestFile md5File(File file) {
        hashFile(file, "md5")
    }

    private TestFile hashFile(TestFile file, String algorithm) {
        def hashFile = getHashFile(file, algorithm)
        hashFile.text = getHash(file, algorithm)
        return hashFile
    }

    protected TestFile getHashFile(TestFile file, String algorithm) {
        file.parentFile.file("${file.name}.${algorithm}")
    }

    protected String getHash(TestFile file, String algorithm) {
        HashUtil.createHash(file, algorithm.toUpperCase()).asHexString()
    }
}
