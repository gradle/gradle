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

package org.gradle.api.publication.maven.internal

import org.gradle.api.publication.maven.internal.model.DefaultMavenArtifact
import org.gradle.api.publication.maven.internal.model.DefaultMavenAuthentication
import org.gradle.api.publication.maven.internal.model.DefaultMavenPublication
import org.gradle.api.publication.maven.internal.model.DefaultMavenRepository
import org.gradle.util.Resources
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile
import org.junit.Rule
import org.sonatype.aether.repository.LocalRepository
import spock.lang.Specification

/**
 * @author: Szczepan Faber, created at: 5/12/11
 */
class DefaultMavenPublisherTest extends Specification {

    @Rule def dir = new TemporaryFolder()
    @Rule def resources = new Resources()

    def publisher = new DefaultMavenPublisher(new LocalRepository("$dir.testDir/local-repository"))

    def "installs artifact"() {
        def publication = new DefaultMavenPublication(groupId: "gradleware.test", artifactId: "fooArtifact", version: "1.1")
        def artifact = new DefaultMavenArtifact(classifier: "", extension: "jar", file: sampleJar())
        publication.mainArtifact = artifact

        when:
        publisher.install(publication)

        then:
        def installed = new File("$dir.testDir/local-repository/gradleware/test/fooArtifact/1.1/fooArtifact-1.1.jar")
        installed.exists()
        installed.bytes == sampleJar().bytes
    }

    def "installs artifact with classifier"() {
        def publication = new DefaultMavenPublication(groupId: "gradleware.test", artifactId: "fooArtifact", version: "1.1")
        def artifact = new DefaultMavenArtifact(classifier: "jdk15", extension: "jar", file: sampleJar())
        publication.mainArtifact = artifact

        when:
        publisher.install(publication)

        then:
        def installed = new File("$dir.testDir/local-repository/gradleware/test/fooArtifact/1.1/fooArtifact-1.1-jdk15.jar")
        installed.exists()
        installed.bytes == sampleJar().bytes
    }

    def "deploys artifact"() {
        def fakeRemoteRepo = new DefaultMavenRepository(url: new File("$dir.testDir/remote-repository").toURI())
        //auth is not used in fakeRemote repo but I wanted to stress the test a tiny bit:
        fakeRemoteRepo.authentication = new DefaultMavenAuthentication(userName: 'szczepiq', password: 'secret')

        def publication = new DefaultMavenPublication(groupId: "gradleware.test", artifactId: "fooArtifact", version: "1.1")
        def artifact = new DefaultMavenArtifact(classifier: "", extension: "jar", file: sampleJar())
        publication.mainArtifact = artifact

        when:
        publisher.deploy(publication, fakeRemoteRepo)

        then:
        def deployed = new File("$dir.testDir/remote-repository/gradleware/test/fooArtifact/1.1/fooArtifact-1.1.jar")
        deployed.exists()
        deployed.bytes == sampleJar().bytes
    }

    def "deploys snapshot along with maven stuff"() {
        def fakeRemoteRepo = new DefaultMavenRepository(url: new File("$dir.testDir/remote-repository").toURI())

        def publication = new DefaultMavenPublication(groupId: "gradleware.test", artifactId: "fooArtifact", version: "1.1-SNAPSHOT")
        def artifact = new DefaultMavenArtifact(classifier: "", extension: "jar", file: sampleJar())
        publication.mainArtifact = artifact

        when:
        publisher.deploy(publication, fakeRemoteRepo)

        then:
        def deployedDir = new File("$dir.testDir/remote-repository/gradleware/test/fooArtifact/1.1-SNAPSHOT")
        def files = deployedDir.list() as List
        ['maven-metadata.xml', 'maven-metadata.xml.md5', 'maven-metadata.xml.sha1'].each {
            assert files.contains(it)
        }
        assert files.any { it =~ /fooArtifact-1.1-.*\.jar/ }
        assert files.any { it =~ /fooArtifact-1.1-.*\.jar.sha1/ }
        assert files.any { it =~ /fooArtifact-1.1-.*\.jar.md5/ }
    }

    def "deploys artifact with classifier"() {
        def fakeRemoteRepo = new DefaultMavenRepository(url: new File("$dir.testDir/remote-repository").toURI())

        def publication = new DefaultMavenPublication(groupId: "gradleware.test", artifactId: "fooArtifact", version: "1.1")
        def artifact = new DefaultMavenArtifact(classifier: "jdk15", extension: "jar", file: sampleJar())
        publication.mainArtifact = artifact

        when:
        publisher.deploy(publication, fakeRemoteRepo)

        then:
        def deployed = new File("$dir.testDir/remote-repository/gradleware/test/fooArtifact/1.1/fooArtifact-1.1-jdk15.jar")
        deployed.exists()
        deployed.bytes == sampleJar().bytes
    }

    def "deals with multiple artifacts"() {
        def fakeRemoteRepo = new DefaultMavenRepository(url: new File("$dir.testDir/remote-repository").toURI())
        def publication = new DefaultMavenPublication(groupId: "gradleware.test", artifactId: "fooArtifact", version: "1.1")
        def artifact = new DefaultMavenArtifact(classifier: "", extension: "jar", file: sampleJar())
        publication.mainArtifact = artifact
        publication.subArtifacts << new DefaultMavenArtifact(classifier: "jdk15", extension: "jar", file: sampleJar())

        when:
        publisher.install(publication)
        publisher.deploy(publication, fakeRemoteRepo)

        then:
        new File("$dir.testDir/local-repository/gradleware/test/fooArtifact/1.1/fooArtifact-1.1.jar").exists()
        new File("$dir.testDir/local-repository/gradleware/test/fooArtifact/1.1/fooArtifact-1.1-jdk15.jar").exists()
        new File("$dir.testDir/remote-repository/gradleware/test/fooArtifact/1.1/fooArtifact-1.1.jar").exists()
        new File("$dir.testDir/remote-repository/gradleware/test/fooArtifact/1.1/fooArtifact-1.1-jdk15.jar").exists()
    }

    TestFile sampleJar() {
        return resources.getResource("sample.jar")
    }

    String dir() {
        return dir.testDir
    }
}
