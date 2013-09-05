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
import org.gradle.test.fixtures.AbstractModule
import org.gradle.test.fixtures.file.TestFile

import java.text.SimpleDateFormat

class MavenLocalModule extends AbstractModule implements MavenModule {
    private static final String MAVEN_METADATA_FILE = "maven-metadata-local.xml"
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
    private final List artifacts = []

    MavenLocalModule(TestFile moduleDir, String groupId, String artifactId, String version) {
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

    MavenModule withNonUniqueSnapshots() {
        //NO-OP for mavenLocal cache.
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
        assertArtifactsPublished("${artifactId}-${publishArtifactVersion}.pom")
        assert parsedPom.packaging == "pom"
    }

    void assertPublishedAsJavaModule() {
        assertPublished()
        assertArtifactsPublished("${artifactId}-${publishArtifactVersion}.jar", "${artifactId}-${publishArtifactVersion}.pom")
        assert parsedPom.packaging == null
    }

    void assertPublishedAsWebModule() {
        assertPublished()
        assertArtifactsPublished("${artifactId}-${publishArtifactVersion}.war", "${artifactId}-${publishArtifactVersion}.pom")
        assert parsedPom.packaging == 'war'
    }

    void assertPublishedAsEarModule() {
        assertPublished()
        assertArtifactsPublished("${artifactId}-${publishArtifactVersion}.ear", "${artifactId}-${publishArtifactVersion}.pom")
        assert parsedPom.packaging == 'ear'
    }

    /**
     * Asserts that exactly the given artifacts have been deployed, along with their checksum files
     */
    void assertArtifactsPublished(String... names) {
        def artifactNames = names as Set
        if (version.endsWith('-SNAPSHOT')) {
            artifactNames.add(MAVEN_METADATA_FILE)
        }
        assert moduleDir.isDirectory()
        Set actual = moduleDir.list() as Set
        for (name in artifactNames) {
            assert actual.remove(name)
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
        moduleDir.file(MAVEN_METADATA_FILE)
    }

    TestFile getRootMetaDataFile() {
        moduleDir.parentFile.file(MAVEN_METADATA_FILE)
    }

    TestFile getArtifactFile(Map options = [:]) {
        if (version.endsWith("-SNAPSHOT") && !metaDataFile.exists()) {
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
        if (version.endsWith("-SNAPSHOT")) {
            return "${version.replaceFirst('-SNAPSHOT$', '')}"
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
        if (version.endsWith("-SNAPSHOT")) {
            publish(metaDataFile) { Writer writer ->
                writer << """
<metadata>
  <!-- ${getArtifactContent()} -->
  <groupId>$groupId</groupId>
  <artifactId>$artifactId</artifactId>
  <version>$version</version>
  <versioning>
    <snapshot>
      <localCopy>true</localCopy>
    </snapshot>
    <lastUpdated>${updateFormat.format(publishTimestamp)}</lastUpdated>
  </versioning>
</metadata>
"""
            }
        }

        publish(pomFile) { Writer writer ->
            def pomPackaging = packaging ?: type;
            writer << """
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <!-- ${getArtifactContent()} -->
  <modelVersion>4.0.0</modelVersion>
  <groupId>$groupId</groupId>
  <artifactId>$artifactId</artifactId>
  <packaging>$pomPackaging</packaging>
  <version>$version</version>
  <description>Published on $publishTimestamp</description>"""

            if (parentPomSection) {
                writer << "\n$parentPomSection\n"
            }

            if (!dependencies.empty) {
                writer << """
  <dependencies>"""
            }

            dependencies.each { dependency ->
                def typeAttribute = dependency['type'] == null ? "" : "<type>$dependency.type</type>"
                writer << """
    <dependency>
      <groupId>$dependency.groupId</groupId>
      <artifactId>$dependency.artifactId</artifactId>
      <version>$dependency.version</version>
      $typeAttribute
    </dependency>"""
            }

            if (!dependencies.empty) {
                writer << """
  </dependencies>"""
            }

            writer << "\n</project>"
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
        publish(rootMavenMetaData) { Writer writer ->
            def builder = new MarkupBuilder(writer)
            builder.metadata {
                groupId(groupId)
                artifactId(artifactId)
                version(allVersions.max())
                versioning {
                    versions {
                        allVersions.each {currVersion ->
                            version(currVersion)
                        }
                    }
                }
            }
        }
    }

    private File publishArtifact(Map<String, ?> artifact) {
        def artifactFile = artifactFile(artifact)
        if (type == 'pom') {
            return artifactFile
        }
        publish(artifactFile) { Writer writer ->
            writer << "${artifactFile.name} : $artifactContent"
        }
        return artifactFile
    }

    @Override
    protected onPublish(TestFile file) {
    }

    private String getArtifactContent() {
        // Some content to include in each artifact, so that its size and content varies on each publish
        return (0..publishCount).join("-")
    }

    private Map<String, Object> toArtifact(Map<String, ?> options) {
        options = new HashMap<String, Object>(options)
        def artifact = [type: options.remove('type') ?: type, classifier: options.remove('classifier') ?: null]
        assert options.isEmpty(): "Unknown options : ${options.keySet()}"
        return artifact
    }
}
