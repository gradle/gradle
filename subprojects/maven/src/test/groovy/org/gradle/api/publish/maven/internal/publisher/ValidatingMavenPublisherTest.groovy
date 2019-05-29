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

import org.codehaus.plexus.util.xml.pull.XmlPullParserException
import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.provider.Providers
import org.gradle.api.publication.maven.internal.VersionRangeMapper
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal
import org.gradle.api.publish.maven.InvalidMavenPublicationException
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.internal.tasks.MavenPomFileGenerator
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import static java.util.Collections.emptySet
import static org.gradle.util.CollectionUtils.toSet

class ValidatingMavenPublisherTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider()

    def delegate = Mock(MavenPublisher)
    def publisher = new ValidatingMavenPublisher(delegate)
    def repository = Mock(MavenArtifactRepository)

    @Unroll
    def "delegates when publication is valid"() {
        when:
        def projectIdentity = makeProjectIdentity("the-group", "the-artifact", "the-version")
        def publication = new MavenNormalizedPublication("pub-name", projectIdentity, "pom", createPomFile(projectIdentity, null, marker), null, emptySet())

        and:
        publisher.publish(publication, repository)

        then:
        delegate.publish(publication, repository)

        where:
        marker << [false, true]
    }

    def "validates project coordinates"() {
        given:
        def projectIdentity = makeProjectIdentity(groupId, artifactId, version)
        def publication = new MavenNormalizedPublication("pub-name", projectIdentity, "pom", createPomFile(projectIdentity), null, emptySet())

        when:
        publisher.publish(publication, repository)

        then:
        def e = thrown InvalidMavenPublicationException
        e.message == "Invalid publication 'pub-name': $message."

        where:
        groupId             | artifactId             | version     | message
        ""                  | "artifact"             | "version"   | "groupId cannot be empty"
        "group"             | ""                     | "version"   | "artifactId cannot be empty"
        "group"             | "artifact"             | ""          | "version cannot be empty"
        "group with spaces" | "artifact"             | "version"   | "groupId (group with spaces) is not a valid Maven identifier ([A-Za-z0-9_\\-.]+)"
        "group-₦ガき∆"        | "artifact"            | "version"   | "groupId (group-₦ガき∆) is not a valid Maven identifier ([A-Za-z0-9_\\-.]+)"
        "group"             | "artifact with spaces" | "version"   | "artifactId (artifact with spaces) is not a valid Maven identifier ([A-Za-z0-9_\\-.]+)"
        "group"             | "artifact-₦ガき∆"       | "version"   | "artifactId (artifact-₦ガき∆) is not a valid Maven identifier ([A-Za-z0-9_\\-.]+)"
        "group"             | "artifact"             | "vers/ion"  | "version cannot contain '/'"
        "group"             | "artifact"             | "vers\\ion"  | "version cannot contain '\\'"
        "group"             | "artifact"             | "version\t" | "version cannot contain ISO control character '\\u0009'"
    }

    def "project coordinates must match POM file"() {
        given:
        def projectIdentity = makeProjectIdentity("group", "artifact", "version")
        def pomFile = createPomFile(makeProjectIdentity(groupId, artifactId, version))
        def publication = new MavenNormalizedPublication("pub-name", projectIdentity, "pom", pomFile, null, emptySet())

        when:
        publisher.publish(publication, repository)

        then:
        def e = thrown InvalidMavenPublicationException
        e.message == "Invalid publication 'pub-name': $message"

        where:
        groupId     | artifactId     | version       | message
        "group-mod" | "artifact"     | "version"     | "supplied groupId (group) does not match value from POM file (group-mod). Cannot edit groupId directly in the POM file."
        "group"     | "artifact-mod" | "version"     | "supplied artifactId (artifact) does not match value from POM file (artifact-mod). Cannot edit artifactId directly in the POM file."
        "group"     | "artifact"     | "version-mod" | "supplied version (version) does not match value from POM file (version-mod). Cannot edit version directly in the POM file."
    }

    def "ignores project coordinates missing from POM file that could be taken from parent POM file"() {
        given:
        def projectIdentity = makeProjectIdentity("group", "artifact", "version")
        def pomFile = createPomFile(makeProjectIdentity(null, "artifact", null), new Action<XmlProvider>() {
            void execute(XmlProvider xml) {
                xml.asNode().appendNode("parent")
            }
        })
        def publication = new MavenNormalizedPublication("pub-name", projectIdentity, "pom", pomFile, null, emptySet())

        when:
        publisher.publish(publication, repository)

        then:
        delegate.publish(publication, repository)
    }

    def "validates artifact attributes"() {
        def projectIdentity = makeProjectIdentity("group", "artifact", "version")
        def pomFile = createPomFile(projectIdentity)
        def mavenArtifact = Stub(MavenArtifact) {
            getExtension() >> extension
            getClassifier() >> classifier
        }
        def publication = new MavenNormalizedPublication("pub-name", projectIdentity, "pom", pomFile, null, toSet([mavenArtifact]))

        when:
        publisher.publish(publication, repository)

        then:
        def t = thrown InvalidMavenPublicationException
        t.message == "Invalid publication 'pub-name': artifact ${message}."

        where:
        extension | classifier     | message
        null      | "classifier"   | "extension cannot be null"
        "ext"     | ""             | "classifier cannot be an empty string. Use null instead"
        "ex\r"    | "classifier"   | "extension cannot contain ISO control character '\\u000d'"
        "ex/"     | "classifier"   | "extension cannot contain '/'"
        "ext"     | "classi\u0090fier" | "classifier cannot contain ISO control character '\\u0090'"
        "ext"     | "class\\ifier" | "classifier cannot contain '\\'"
    }

    @Unroll
    def "cannot publish with file that #message"() {
        def projectIdentity = makeProjectIdentity("group", "artifact", "version")
        def pomFile = createPomFile(projectIdentity)
        def mavenArtifact = Mock(MavenArtifact)
        def publication = new MavenNormalizedPublication("pub-name", projectIdentity, "pom", pomFile, null, toSet([mavenArtifact]))

        File theFile = new TestFile(testDir.testDirectory, "testFile")
        if (createDir) {
            theFile.createDir()
        }

        when:
        publisher.publish(publication, repository)

        then:
        mavenArtifact.extension >> "ext"
        mavenArtifact.file >> theFile

        and:
        def t = thrown InvalidMavenPublicationException
        t.message == "Invalid publication 'pub-name': artifact file ${message}: '${theFile}'"

        where:
        message          | createDir
        'does not exist' | false
        'is a directory' | true
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
        def projectIdentity = makeProjectIdentity("group", "artifact", "version")
        def pomFile = createPomFile(projectIdentity)
        def publication = new MavenNormalizedPublication("pub-name", projectIdentity, "pom", pomFile, null, toSet([artifact1, artifact2]))

        when:
        publisher.publish(publication, repository)

        then:
        def t = thrown InvalidMavenPublicationException
        t.message == "Invalid publication 'pub-name': multiple artifacts with the identical extension and classifier ('ext1', 'classified')."
    }

    def "cannot publish extra artifact with same attributes as POM"() {
        given:
        MavenArtifact artifact1 = Stub() {
            getExtension() >> "pom"
            getClassifier() >> null
            getFile() >> testDir.createFile('artifact1')
        }
        def projectIdentity = makeProjectIdentity("group", "artifact", "version")
        def pomFile = createPomFile(projectIdentity)
        def publication = new MavenNormalizedPublication("pub-name", projectIdentity, "pom", pomFile, null, toSet([artifact1, pomFile]))

        when:
        publisher.publish(publication, repository)

        then:
        def t = thrown InvalidMavenPublicationException
        t.message == "Invalid publication 'pub-name': multiple artifacts with the identical extension and classifier ('pom', 'null')."
    }

    def "supplied POM file must be valid"() {
        given:
        def projectIdentity = makeProjectIdentity("group", "artifact", "version")
        def pomFile = createPomFile(projectIdentity, new Action<XmlProvider>() {
            void execute(XmlProvider xml) {
                xml.asNode().appendNode("invalid", "This is not a valid pomFile element")
            }
        })
        def publication = new MavenNormalizedPublication("pub-name", projectIdentity, "pom", pomFile, null, emptySet())

        when:
        publisher.publish(publication, repository)

        then:
        def e = thrown InvalidMavenPublicationException
        e.message == "Invalid publication 'pub-name': POM file is invalid. Check any modifications you have made to the POM file."
        e.cause instanceof XmlPullParserException
        e.cause.message =~ "Unrecognised tag: 'invalid' .*"
    }

    private def makeProjectIdentity(def groupId, def artifactId, def version) {
        return Stub(MavenProjectIdentity) {
            getGroupId() >> Providers.of(groupId)
            getArtifactId() >> Providers.of(artifactId)
            getVersion() >> Providers.of(version)
        }
    }

    private def createPomFile(MavenProjectIdentity projectIdentity, Action<XmlProvider> withXmlAction = null, boolean marker = false) {
        def pomFile = testDir.file("pom")
        def mapper = Stub(VersionRangeMapper)
        MavenPomFileGenerator pomFileGenerator = new MavenPomFileGenerator(projectIdentity, mapper, Stub(VersionMappingStrategyInternal), ImmutableAttributes.EMPTY, ImmutableAttributes.EMPTY, marker)
        if (withXmlAction != null) {
            pomFileGenerator.withXml(withXmlAction)
        }
        pomFileGenerator.writeTo(pomFile)
        return createArtifact(pomFile, "pom")
    }

    private def createArtifact(File file, String extension) {
        return Mock(MavenArtifact) {
            getFile() >> file
            getExtension() >> extension
        }
    }
}
