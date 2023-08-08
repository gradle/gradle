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

package org.gradle.api.publish.ivy.internal.publisher

import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.publish.ivy.InvalidIvyPublicationException
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.internal.artifact.FileBasedIvyArtifact
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyModuleDescriptorSpec
import org.gradle.api.publish.ivy.internal.publication.IvyModuleDescriptorSpecInternal
import org.gradle.api.publish.ivy.internal.tasks.IvyDescriptorFileGenerator
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

import static java.util.Collections.emptySet

class ValidatingIvyPublisherTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())

    def delegate = Mock(IvyPublisher)
    DefaultImmutableModuleIdentifierFactory moduleIdentifierFactory = new DefaultImmutableModuleIdentifierFactory()
    IvyMutableModuleMetadataFactory metadataFactory = DependencyManagementTestUtil.ivyMetadataFactory()

    def publisher = new ValidatingIvyPublisher(delegate, moduleIdentifierFactory, TestFiles.fileRepository(), metadataFactory)
    def repository = Mock(IvyArtifactRepository)
    def coordinates = DefaultModuleVersionIdentifier.newId("the-group", "the-artifact", "the-version")

    def "delegates when publication is valid"() {
        when:
        def descriptor = ivyDescriptor()
        descriptor.branch = "the-branch"
        descriptor.status = "release"
        def publication = new IvyNormalizedPublication("pub-name", coordinates, ivyFile(descriptor), emptySet())

        and:
        publisher.publish(publication, repository)

        then:
        delegate.publish(publication, repository)
    }

    def "does not attempt to resolve extended ivy descriptor when validating"() {
        when:
        def descriptor = ivyDescriptor()
        descriptor.withXml(new Action<XmlProvider>() {
            void execute(XmlProvider t) {
                t.asNode().info[0].appendNode("extends", ["organisation": "parent-org", "module": "parent-module", "revision": "parent-revision"])
            }
        })

        and:
        def publication = new IvyNormalizedPublication("pub-name", coordinates, ivyFile(descriptor), emptySet())

        and:
        publisher.publish(publication, repository)

        then:
        delegate.publish(publication, repository)
    }

    def "validates project coordinates"() {
        given:
        def coordinates = DefaultModuleVersionIdentifier.newId(group, name, version)

        def descriptor = ivyDescriptor()
        descriptor.coordinates.getOrganisation().set(group)
        descriptor.coordinates.getModule().set(name)
        descriptor.coordinates.getRevision().set(version)

        def publication = new IvyNormalizedPublication("pub-name", coordinates, ivyFile(descriptor), emptySet())

        when:
        publisher.publish(publication, repository)

        then:
        def e = thrown InvalidIvyPublicationException
        e.message == "Invalid publication 'pub-name': $message."

        where:
        group          | name       | version         | message
        ""             | "module"   | "version"       | "organisation cannot be empty"
        "organisation" | ""         | "version"       | "module name cannot be empty"
        "organisation" | "module"   | ""              | "revision cannot be empty"
        "org\t"        | "module"   | "version"       | "organisation cannot contain ISO control character '\\u0009'"
        "organisation" | "module\n" | "version"       | "module name cannot contain ISO control character '\\u000a'"
        "organisation" | "module"   | "version\u0085" | "revision cannot contain ISO control character '\\u0085'"
    }

    def "validates ivy metadata"() {
        given:
        def descriptor = ivyDescriptor()
        descriptor.branch = branch
        descriptor.status = status
        def publication = new IvyNormalizedPublication("pub-name", coordinates, ivyFile(descriptor), emptySet())

        when:
        publisher.publish(publication, repository)

        then:
        def e = thrown InvalidIvyPublicationException
        e.message == "Invalid publication 'pub-name': $message."

        where:
        branch             | status          | message
        ""                 | "release"       | "branch cannot be an empty string. Use null instead"
        "someBranch"       | ""              | "status cannot be an empty string. Use null instead"
        "someBranch\t"     | "release"       | "branch cannot contain ISO control character '\\u0009'"
        "someBranch"       | "release\t"     | "status cannot contain ISO control character '\\u0009'"
        "someBranch\n"     | "release"       | "branch cannot contain ISO control character '\\u000a'"
        "someBranch"       | "release\n"     | "status cannot contain ISO control character '\\u000a'"
        "someBranch\u0085" | "release"       | "branch cannot contain ISO control character '\\u0085'"
        "someBranch"       | "release\u0085" | "status cannot contain ISO control character '\\u0085'"
        "someBran\\ch"     | "release"       | "branch cannot contain '\\'"
        "someBranch"       | "relea\\se"     | "status cannot contain '\\'"
        "someBranch"       | "relea/se"      | "status cannot contain '/'"
    }

    def "delegates with valid ivy metadata" () {
        given:
        def descriptor = ivyDescriptor()
        descriptor.branch = branch
        descriptor.status = status
        def publication = new IvyNormalizedPublication("pub-name", coordinates, ivyFile(descriptor), emptySet())

        when:
        publisher.publish(publication, repository)

        then:
        delegate.publish(publication, repository)

        where:
        branch                  | status
        null                    | null
        "someBranch"            | "release"
        "feature/someBranch"    | "release"
        "someBranch_ぴ₦ガき∆ç√∫" | "release_ぴ₦ガき∆ç√∫"
    }

    def "delegates with valid extra info elements" () {
        given:
        def descriptor = ivyDescriptor()
        elements.each { descriptor.extraInfo(it, it, "${it}Value") }
        def publication = new IvyNormalizedPublication("pub-name", coordinates, ivyFile(descriptor), emptySet())

        when:
        publisher.publish(publication, repository)

        then:
        delegate.publish(publication, repository)

        where:
        elements             | _
        [ ]                  | _
        [ 'foo' ]            | _
        [ 'foo', 'bar' ]     | _
    }

    def "project coordinates must match ivy descriptor file"() {
        given:
        def descriptor = ivyDescriptor()
        descriptor.coordinates.getOrganisation().set(organisation)
        descriptor.coordinates.getModule().set(module)
        descriptor.coordinates.getRevision().set(version)
        def publication = new IvyNormalizedPublication("pub-name", coordinates, ivyFile(descriptor), emptySet())

        when:
        publisher.publish(publication, repository)

        then:
        def e = thrown InvalidIvyPublicationException
        e.message == "Invalid publication 'pub-name': $message"

        where:
        organisation | module       | version       | message
        "org-mod"    | "the-artifact" | "the-version" | "supplied organisation does not match ivy descriptor (cannot edit organisation directly in the ivy descriptor file)."
        "the-group"  | "module-mod"   | "the-version" | "supplied module name does not match ivy descriptor (cannot edit module name directly in the ivy descriptor file)."
        "the-group"  | "the-artifact" | "version-mod" | "supplied revision does not match ivy descriptor (cannot edit revision directly in the ivy descriptor file)."
    }

    def "reports and fails with invalid descriptor file (marker = #marker)"() {
        given:
        def projectIdentity = this.projectIdentity(coordinates.group, coordinates.name, coordinates.version)
        def artifact = new FileBasedIvyArtifact(new File("foo.txt"), projectIdentity, TestFiles.taskDependencyFactory())
        artifact.setConf("unknown")
        def descriptor = ivyDescriptor()
        descriptor.artifacts.set([artifact])
        def ivyFile = ivyFile(descriptor)

        and:
        def publication = new IvyNormalizedPublication("pub-name", coordinates, ivyFile, emptySet())

        when:
        publisher.publish(publication, repository)

        then:
        def e = thrown InvalidIvyPublicationException
        e.message == "Invalid publication 'pub-name': Could not parse Ivy file ${ivyFile}"

        where:
        marker << [false, true]
    }

    def "validates artifact attributes"() {
        given:

        def ivyArtifact = Stub(IvyArtifact) {
            getName() >> name
            getType() >> type
            getExtension() >> extension
            getClassifier() >> classifier
        }
        def publication = new IvyNormalizedPublication("pub-name", coordinates, ivyFile(), [ivyArtifact] as Set)

        when:
        publisher.publish(publication, repository)

        then:
        def t = thrown InvalidIvyPublicationException
        t.message == "Invalid publication 'pub-name': artifact ${message}."

        where:
        name    | type    | extension | classifier   | message
        null    | "type"  | "ext"     | "classifier" | "name cannot be null"
        ""      | "type"  | "ext"     | "classifier" | "name cannot be empty"
        "na/me" | "type"  | "ext"     | null         | "name cannot contain '/'"
        "name"  | null    | "ext"     | "classifier" | "type cannot be null"
        "name"  | ""      | "ext"     | "classifier" | "type cannot be empty"
        "name"  | "ty/pe" | "ext"     | null         | "type cannot contain '/'"
        "name"  | "type"  | null      | "classifier" | "extension cannot be null"
        "name"  | "type"  | "ex/t"    | null         | "extension cannot contain '/'"
        "name"  | "type"  | "ext"     | ""           | "classifier cannot be an empty string. Use null instead"
        "name"  | "type"  | "ext"     | "class\\y"   | "classifier cannot contain '\\'"
    }

    def "cannot publish with file that is a directory"() {
        def ivyArtifact = Mock(IvyArtifact)
        def publication = new IvyNormalizedPublication("pub-name", coordinates, ivyFile(), [ivyArtifact] as Set)

        File someDir = new TestFile(testDirectoryProvider.testDirectory, "testFile")
        someDir.createDir()

        when:
        publisher.publish(publication, repository)

        then:
        ivyArtifact.name >> "name"
        ivyArtifact.type >> "type"
        ivyArtifact.extension >> "ext"
        ivyArtifact.file >> someDir

        and:
        def t = thrown InvalidIvyPublicationException
        t.message == "Invalid publication 'pub-name': artifact file is a directory: '${someDir}'"

    }

    def "cannot publish with duplicate artifacts"() {
        given:
        IvyArtifact artifact1 = Stub() {
            getName() >> "name"
            getExtension() >> "ext1"
            getType() >> "type"
            getClassifier() >> "classified"
            getFile() >> testDirectoryProvider.createFile('artifact1')
        }
        IvyArtifact artifact2 = Stub() {
            getName() >> "name"
            getExtension() >> "ext1"
            getType() >> "type"
            getClassifier() >> "classified"
            getFile() >> testDirectoryProvider.createFile('artifact2')
        }
        def publication = new IvyNormalizedPublication("pub-name", coordinates, ivyFile(), [artifact1, artifact2] as Set)

        when:
        publisher.publish(publication, repository)

        then:
        def t = thrown InvalidIvyPublicationException
        t.message == "Invalid publication 'pub-name': multiple artifacts with the identical name, extension, type and classifier ('name', ext1', 'type', 'classified')."
    }

    def "cannot publish artifact with same attributes as ivy.xml"() {
        given:
        IvyArtifact artifact1 = Stub() {
            getName() >> "ivy"
            getExtension() >> "xml"
            getType() >> "xml"
            getClassifier() >> null
            getFile() >> testDirectoryProvider.createFile('artifact1')
        }
        def publication = new IvyNormalizedPublication("pub-name", coordinates, ivyFile(), [artifact1] as Set)

        when:
        publisher.publish(publication, repository)

        then:
        def t = thrown InvalidIvyPublicationException
        t.message == "Invalid publication 'pub-name': multiple artifacts with the identical name, extension, type and classifier ('ivy', xml', 'xml', 'null')."
    }

    private def projectIdentity(String groupId, String artifactId, String version) {
        def coordinates = TestUtil.objectFactory().newInstance(IvyPublicationCoordinates)
        coordinates.getOrganisation().set(groupId)
        coordinates.getModule().set(artifactId)
        coordinates.getRevision().set(version)
        coordinates
    }

    private def ivyFile() {
        return ivyFile(ivyDescriptor())
    }

    private IvyModuleDescriptorSpecInternal ivyDescriptor() {
        IvyPublicationCoordinates publicationCoordinates = TestUtil.objectFactory().newInstance(IvyPublicationCoordinates)
        publicationCoordinates.organisation.set(coordinates.group)
        publicationCoordinates.module.set(coordinates.name)
        publicationCoordinates.revision.set(coordinates.version)

        IvyModuleDescriptorSpecInternal descriptor = TestUtil.objectFactory().newInstance(
            DefaultIvyModuleDescriptorSpec.class,
            TestUtil.objectFactory(),
            publicationCoordinates
        )

        descriptor.writeGradleMetadataMarker.set(true)

        descriptor
    }

    private def ivyFile(IvyModuleDescriptorSpecInternal descriptor) {
        def ivyXmlFile = testDirectoryProvider.file("ivy")
        IvyDescriptorFileGenerator.generateSpec(descriptor).writeTo(ivyXmlFile)
        return ivyXmlFile
    }
}
