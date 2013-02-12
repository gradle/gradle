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

package org.gradle.api.publish.maven.internal.publisher
import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.publish.maven.InvalidMavenPublicationException
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.internal.MavenProjectIdentity
import org.gradle.api.publish.maven.internal.tasks.MavenPomFileGenerator
import org.gradle.mvn3.org.codehaus.plexus.util.xml.pull.XmlPullParserException
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.CollectionUtils
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

public class ValidatingMavenPublisherTest extends Specification {
    @Shared TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider()
    def delegate = Mock(MavenPublisher)
    def publisher = new ValidatingMavenPublisher(delegate)

    def "delegates when publication is valid"() {
        when:
        def projectIdentity = projectIdentity("the-group", "the-artifact", "the-version")
        def publication = new MavenNormalizedPublication("pub-name", createPomFile("the-group", "the-artifact", "the-version"), projectIdentity, Collections.emptySet())
        def repository = Mock(MavenArtifactRepository)

        and:
        publisher.publish(publication, repository)

        then:
        delegate.publish(publication, repository)
    }

    def "validates project coordinates"() {
        given:
        def projectIdentity = projectIdentity(groupId, artifactId, version)
        def publication = new MavenNormalizedPublication("pub-name", createPomFile(groupId, artifactId, version), projectIdentity, Collections.emptySet())

        def repository = Mock(MavenArtifactRepository)

        when:
        publisher.publish(publication, repository)

        then:
        def e = thrown InvalidMavenPublicationException
        e.message == message

        where:
        groupId             | artifactId             | version   | message
        ""                  | "artifact"             | "version" | "The groupId value cannot be empty"
        "group"             | ""                     | "version" | "The artifactId value cannot be empty"
        "group"             | "artifact"             | ""        | "The version value cannot be empty"
        "group with spaces" | "artifact"             | "version" | "The groupId value is not a valid Maven identifier ([A-Za-z0-9_\\-.]+)"
        "group-₦ガき∆"        | "artifact"             | "version" | "The groupId value is not a valid Maven identifier ([A-Za-z0-9_\\-.]+)"
        "group"             | "artifact with spaces" | "version" | "The artifactId value is not a valid Maven identifier ([A-Za-z0-9_\\-.]+)"
        "group"             | "artifact-₦ガき∆"        | "version" | "The artifactId value is not a valid Maven identifier ([A-Za-z0-9_\\-.]+)"
    }

    def "project coordinates must match POM file"() {
        given:
        def projectIdentity = projectIdentity("group", "artifact", "version")
        def pomFile = createPomFile(groupId, artifactId, version)
        def publication = new MavenNormalizedPublication("pub-name", pomFile, projectIdentity, Collections.emptySet())

        def repository = Mock(MavenArtifactRepository)

        when:
        publisher.publish(publication, repository)

        then:
        def e = thrown InvalidMavenPublicationException
        e.message == message

        where:
        groupId     | artifactId     | version       | message
        "group-mod" | "artifact"     | "version"     | "Publication groupId does not match POM file value. Cannot edit groupId directly in the POM file."
        "group"     | "artifact-mod" | "version"     | "Publication artifactId does not match POM file value. Cannot edit artifactId directly in the POM file."
        "group"     | "artifact"     | "version-mod" | "Publication version does not match POM file value. Cannot edit version directly in the POM file."
    }


    @Unroll
    def "cannot publish with file that #message"() {
        def projectIdentity = projectIdentity("group", "artifact", "version")
        def pomFile = createPomFile("group", "artifact", "version")
        def mavenArtifact = Mock(MavenArtifact)
        def publication = new MavenNormalizedPublication("pub-name", pomFile, projectIdentity, Collections.singleton(mavenArtifact))

        when:
        publisher.publish(publication, Mock(MavenArtifactRepository))

        then:
        mavenArtifact.file >> theFile

        and:
        def t = thrown InvalidMavenPublicationException
        t.message == "Cannot publish maven publication 'pub-name': artifact file ${message}: '${theFile}'"

        where:
        theFile                                                         | message
        new File(testDir.testDirectory, 'does-not-exist') | 'does not exist'
        testDir.testDirectory.createDir('sub_directory')  | 'is a directory'
    }

    def "cannot publish with duplicate artifacts"() {
        given:
        MavenArtifact artifact1 = Stub() {
            getExtension() >> "ext1"
            getClassifier() >> "classified"
            getFile() >> testDir.createFile('artifact1')
        }
        MavenArtifact artifact2 = Stub() {
            getExtension() >> "ext1"
            getClassifier() >> "classified"
            getFile() >> testDir.createFile('artifact2')
        }
        def projectIdentity = projectIdentity("group", "artifact", "version")
        def pomFile = createPomFile("group", "artifact", "version")
        def publication = new MavenNormalizedPublication("pub-name", pomFile, projectIdentity, CollectionUtils.toSet([artifact1, artifact2]))

        when:
        publisher.publish(publication, Mock(MavenArtifactRepository))

        then:
        def t = thrown InvalidMavenPublicationException
        t.message == "Cannot publish maven publication 'pub-name': multiple artifacts with the identical extension 'ext1' and classifier 'classified'."
    }

    def "supplied POM file must be valid"() {
        given:
        def projectIdentity = projectIdentity("group", "artifact", "version")
        def pomFile = createPomFile("group", "artifact", "version", new Action<XmlProvider>() {
            void execute(XmlProvider xml) {
                xml.asNode().appendNode("invalid", "This is not a valid pomFile element")
            }
        })
        def publication = new MavenNormalizedPublication("pub-name", pomFile, projectIdentity, Collections.emptySet())

        def repository = Mock(MavenArtifactRepository)

        when:
        publisher.publish(publication, repository)

        then:
        def e = thrown InvalidMavenPublicationException
        e.message == "POM file is invalid. Check any modifications you have made to the POM file."
        e.cause instanceof XmlPullParserException
        e.cause.message =~ "Unrecognised tag: 'invalid' .*"
    }

    private def projectIdentity(def groupId, def artifactId, def version) {
        return Stub(MavenProjectIdentity) {
            getGroupId() >> groupId
            getArtifactId() >> artifactId
            getVersion() >> version
        }
    }

    private def createPomFile(def groupId, def artifactId, def version, Action<XmlProvider> withXmlAction = null) {
        def pomFile = testDir.file("pom")
        MavenPomFileGenerator pomFileGenerator = new MavenPomFileGenerator();
        pomFileGenerator.groupId = groupId
        pomFileGenerator.artifactId = artifactId
        pomFileGenerator.version = version
        if (withXmlAction != null) {
            pomFileGenerator.withXml(withXmlAction)
        }
        pomFileGenerator.writeTo(pomFile)
        return pomFile
    }
}
