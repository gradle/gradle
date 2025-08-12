/*
 * Copyright 2013 the original author or authors.
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

import groovy.xml.XmlSlurper
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class MavenFileModuleTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    TestFile testFile
    MavenModule mavenFileModule
    MavenModule snapshotMavenFileModule

    def setup() {
        testFile = tmpDir.file("file")
        mavenFileModule = new MavenFileModule(testFile, testFile, "my-company", "my-artifact", "1.0")
        snapshotMavenFileModule = new MavenFileModule(testFile, testFile, "my-company", "my-artifact", "1.0-SNAPSHOT")
    }

    def "Add multiple dependencies without type"() {
        when:
        List dependencies = mavenFileModule.dependsOnModules("dep1", "dep2").dependencies

        then:
        dependencies != null
        dependencies.size() == 2
        dependencies.get(0).groupId == 'my-company'
        dependencies.get(0).artifactId == 'dep1'
        dependencies.get(0).type == null
        dependencies.get(0).version == '1.0'
        dependencies.get(1).groupId == 'my-company'
        dependencies.get(1).artifactId == 'dep2'
        dependencies.get(1).type == null
        dependencies.get(1).version == '1.0'
    }

    def "Add single dependency"() {
        when:
        List dependencies = mavenFileModule.dependsOn('my-company', 'dep1', '1.0', 'jar', 'compile', null).dependencies

        then:
        dependencies != null
        dependencies.size() == 1
        dependencies.get(0) == [groupId: 'my-company', artifactId: 'dep1', version: '1.0', type: 'jar', scope: 'compile', classifier: null, exclusions: null]
    }

    def "Check packaging for set packaging"() {
        when:
        String packaging = mavenFileModule.hasPackaging('war').packaging

        then:
        packaging != null
        packaging == 'war'
    }

    def "Check packaging for no set packaging"() {
        when:
        String packaging = mavenFileModule.packaging

        then:
        packaging == null
    }

    def "Check type for set type"() {
        when:
        String type = mavenFileModule.hasType('war').type

        then:
        type != null
        type == 'war'
    }

    def "Check type for no set type"() {
        when:
        String type = mavenFileModule.type

        then:
        type != null
        type == 'jar'
    }

    def "Provides unique snapshots by default"() {
        when:
        boolean uniqueSnapshots = mavenFileModule.uniqueSnapshots

        then:
        uniqueSnapshots
    }

    def "Sets non-unique snapshots"() {
        when:
        boolean uniqueSnapshots = mavenFileModule.withNonUniqueSnapshots().uniqueSnapshots

        then:
        !uniqueSnapshots
    }

    def "On publishing SHA1 and MD5 files are created"() {
        given:
        TestFile pomTestFile = tmpDir.createFile("build/test/pom.xml")

        when:
        mavenFileModule.onPublish(pomTestFile)
        def testFiles = Arrays.asList(pomTestFile.parentFile.listFiles())

        then:
        testFiles*.name.containsAll('pom.xml.md5', 'pom.xml.sha1')
    }

    def "Get artifact file for non-snapshot"() {
        when:
        TestFile artifactFile = mavenFileModule.getArtifactFile([:])

        then:
        artifactFile != null
        artifactFile.name == 'my-artifact-1.0.jar'
    }

    def "Get artifact file for snapshot"() {
        when:
        TestFile artifactFile = snapshotMavenFileModule.getArtifactFile()

        then:
        artifactFile != null
        artifactFile.name == 'my-artifact-1.0-SNAPSHOT.jar'
    }

    def "Get publish artifact version for non-snapshot"() {
        when:
        String version = mavenFileModule.getPublishArtifactVersion()

        then:
        version == '1.0'
    }

    def "Get publish artifact version for unique snapshot"() {
        when:
        String version = snapshotMavenFileModule.getPublishArtifactVersion()

        then:
        version == '1.0-20100101.120001-1'
    }

    def "Get publish artifact version for non-unique snapshot"() {
        given:
        snapshotMavenFileModule.withNonUniqueSnapshots()

        when:
        String version = snapshotMavenFileModule.getPublishArtifactVersion()

        then:
        version == '1.0-SNAPSHOT'
    }

    def "Publish artifacts for non-snapshot"() {
        when:
        MavenModule mavenModule = mavenFileModule.withoutExtraChecksums().publish()
        def publishedFiles = Arrays.asList(testFile.listFiles())

        then:
        mavenModule != null
        publishedFiles*.name.containsAll('my-artifact-1.0.jar', 'my-artifact-1.0.jar.sha1', 'my-artifact-1.0.jar.md5', 'my-artifact-1.0.pom', 'my-artifact-1.0.pom.sha1', 'my-artifact-1.0.pom.md5')
        !publishedFiles.find { it.name == 'maven-metadata.xml' }
        mavenFileModule.assertArtifactsPublished('my-artifact-1.0.jar', 'my-artifact-1.0.pom')
    }

    def "Publish artifacts for unique snapshot"() {
        when:
        MavenModule mavenModule = snapshotMavenFileModule.withoutExtraChecksums().publish()
        def publishedFiles = Arrays.asList(testFile.listFiles())

        then:
        mavenModule != null
        publishedFiles*.name.containsAll('my-artifact-1.0-20100101.120001-1.jar', 'my-artifact-1.0-20100101.120001-1.jar.sha1', 'my-artifact-1.0-20100101.120001-1.jar.md5', 'my-artifact-1.0-20100101.120001-1.pom', 'my-artifact-1.0-20100101.120001-1.pom.sha1', 'my-artifact-1.0-20100101.120001-1.pom.md5')
        publishedFiles.find { it.name == 'maven-metadata.xml' }.exists()
        new XmlSlurper().parseText(publishedFiles.find { it.name == 'maven-metadata.xml' }.text).versioning.snapshot.timestamp.text() == '20100101.120001'
        new XmlSlurper().parseText(publishedFiles.find { it.name == 'maven-metadata.xml' }.text).versioning.snapshot.buildNumber.text() == '1'
        snapshotMavenFileModule.assertArtifactsPublished('maven-metadata.xml', 'my-artifact-1.0-20100101.120001-1.jar', 'my-artifact-1.0-20100101.120001-1.pom')
    }

    def "Publish artifacts for non-unique snapshot"() {
        given:
        snapshotMavenFileModule.withNonUniqueSnapshots()

        when:
        MavenModule mavenModule = snapshotMavenFileModule.withoutExtraChecksums().publish()
        def publishedFiles = Arrays.asList(testFile.listFiles())

        then:
        mavenModule != null
        publishedFiles*.name.containsAll('my-artifact-1.0-SNAPSHOT.jar', 'my-artifact-1.0-SNAPSHOT.jar.sha1', 'my-artifact-1.0-SNAPSHOT.jar.md5', 'my-artifact-1.0-SNAPSHOT.pom', 'my-artifact-1.0-SNAPSHOT.pom.sha1', 'my-artifact-1.0-SNAPSHOT.pom.md5')
        !publishedFiles.find { it.name == 'maven-metadata.xml' }
        snapshotMavenFileModule.assertArtifactsPublished('my-artifact-1.0-SNAPSHOT.jar', 'my-artifact-1.0-SNAPSHOT.pom')
    }
}
