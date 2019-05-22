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
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.publish.ivy.InvalidIvyPublicationException
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.internal.artifact.FileBasedIvyArtifact
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.AttributeTestUtil
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import javax.xml.namespace.QName

import static java.util.Collections.emptySet
import static org.gradle.util.CollectionUtils.toSet

class ValidatingIvyPublisherTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()

    def delegate = Mock(IvyPublisher)
    DefaultImmutableModuleIdentifierFactory moduleIdentifierFactory = new DefaultImmutableModuleIdentifierFactory()
    IvyMutableModuleMetadataFactory metadataFactory = new IvyMutableModuleMetadataFactory(moduleIdentifierFactory, AttributeTestUtil.attributesFactory())

    def publisher = new ValidatingIvyPublisher(delegate, moduleIdentifierFactory, TestFiles.fileRepository(), metadataFactory)
    def repository = Mock(IvyArtifactRepository)

    def "delegates when publication is valid"() {
        when:
        def projectIdentity = this.projectIdentity("the-group", "the-artifact", "the-version")
        def generator = ivyGenerator("the-group", "the-artifact", "the-version")
                            .withBranch("the-branch")
                            .withStatus("release")
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity, ivyFile(generator), emptySet())

        and:
        publisher.publish(publication, repository)

        then:
        delegate.publish(publication, repository)
    }

    def "does not attempt to resolve extended ivy descriptor when validating"() {
        when:
        def ivyFile = ivyFile(ivyGenerator("the-group", "the-artifact", "the-version")
                .withAction(new Action<XmlProvider>() {
                    void execute(XmlProvider t) {
                        t.asNode().info[0].appendNode("extends", ["organisation": "parent-org", "module": "parent-module", "revision": "parent-revision"])
                    }
                }))

        and:
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity("the-group", "the-artifact", "the-version"), ivyFile, emptySet())

        and:
        publisher.publish(publication, repository)

        then:
        delegate.publish(publication, repository)
    }

    def "validates project coordinates"() {
        given:
        def projectIdentity = projectIdentity(group, name, version)
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity, ivyFile(group, name, version), emptySet())

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
        null           | "module"   | "version"       | "organisation cannot be null"
        "organisation" | null       | "version"       | "module name cannot be null"
        "organisation" | "module"   | null            | "revision cannot be null"
        "org\t"        | "module"   | "version"       | "organisation cannot contain ISO control character '\\u0009'"
        "organisation" | "module\n" | "version"       | "module name cannot contain ISO control character '\\u000a'"
        "organisation" | "module"   | "version\u0085" | "revision cannot contain ISO control character '\\u0085'"
    }

    def "validates ivy metadata"() {
        given:
        def projectIdentity = projectIdentity("org", "module", "version")
        def generator = ivyGenerator("org", "module", "version")
                            .withBranch(branch)
                            .withStatus(status)
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity, ivyFile(generator), emptySet())

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
        def projectIdentity = projectIdentity("org", "module", "version")
        def generator = ivyGenerator("org", "module", "version")
                            .withBranch(branch)
                            .withStatus(status)
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity, ivyFile(generator), emptySet())

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
        def projectIdentity = projectIdentity("org", "module", "version")
        def generator = ivyGenerator("org", "module", "version")
        elements.each { generator.withExtraInfo(it, "${it}Value") }
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity, ivyFile(generator), emptySet())

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
        def projectIdentity = projectIdentity("org", "module", "version")
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity, ivyFile(organisation, module, version), emptySet())

        when:
        publisher.publish(publication, repository)

        then:
        def e = thrown InvalidIvyPublicationException
        e.message == "Invalid publication 'pub-name': $message"

        where:
        organisation | module       | version       | message
        "org-mod"    | "module"     | "version"     | "supplied organisation does not match ivy descriptor (cannot edit organisation directly in the ivy descriptor file)."
        "org"        | "module-mod" | "version"     | "supplied module name does not match ivy descriptor (cannot edit module name directly in the ivy descriptor file)."
        "org"        | "module"     | "version-mod" | "supplied revision does not match ivy descriptor (cannot edit revision directly in the ivy descriptor file)."
    }

    @Unroll
    def "reports and fails with invalid descriptor file (marker = #marker)"() {
        given:
        def identity = new DefaultIvyPublicationIdentity("the-group", "the-artifact", "the-version")
        IvyDescriptorFileGenerator ivyFileGenerator = new IvyDescriptorFileGenerator(identity, marker, null)
        def artifact = new FileBasedIvyArtifact(new File("foo.txt"), identity)
        artifact.setConf("unknown")
        ivyFileGenerator.addArtifact(artifact)
        def ivyFile = ivyFile(ivyFileGenerator)

        and:
        def publication = new IvyNormalizedPublication("pub-name", identity, ivyFile, emptySet())

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
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity("org", "module", "version"), ivyFile("org", "module", "version"), toSet([ivyArtifact]))

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

    @Unroll
    def "cannot publish with file that #message"() {
        def ivyArtifact = Mock(IvyArtifact)
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity("group", "artifact", "version"), ivyFile("group", "artifact", "version"), toSet([ivyArtifact]))

        File theFile = new TestFile(testDirectoryProvider.testDirectory, "testFile")
        if (createDir) {
            theFile.createDir()
        }

        when:
        publisher.publish(publication, repository)

        then:
        ivyArtifact.name >> "name"
        ivyArtifact.type >> "type"
        ivyArtifact.extension >> "ext"
        ivyArtifact.file >> theFile

        and:
        def t = thrown InvalidIvyPublicationException
        t.message == "Invalid publication 'pub-name': artifact file ${message}: '${theFile}'"

        where:
        message          | createDir
        'does not exist' | false
        'is a directory' | true
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
        def projectIdentity = projectIdentity("org", "module", "revision")
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity, ivyFile("org", "module", "revision"), toSet([artifact1, artifact2]))

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
        def projectIdentity = projectIdentity("org", "module", "revision")
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity, ivyFile("org", "module", "revision"), toSet([artifact1]))

        when:
        publisher.publish(publication, repository)

        then:
        def t = thrown InvalidIvyPublicationException
        t.message == "Invalid publication 'pub-name': multiple artifacts with the identical name, extension, type and classifier ('ivy', xml', 'xml', 'null')."
    }

    private def projectIdentity(def groupId, def artifactId, def version) {
        return Stub(IvyPublicationIdentity) {
            getOrganisation() >> groupId
            getModule() >> artifactId
            getRevision() >> version
        }
    }

    private TestIvyDescriptorFileGenerator ivyGenerator(def group, def moduleName, def version) {
        return new TestIvyDescriptorFileGenerator(new DefaultIvyPublicationIdentity(group, moduleName, version))
    }

    private def ivyFile(def group, def moduleName, def version) {
        return ivyFile(ivyGenerator(group, moduleName, version))
    }

    private def ivyFile(IvyDescriptorFileGenerator ivyFileGenerator) {
        def ivyXmlFile = testDirectoryProvider.file("ivy")
        ivyFileGenerator.writeTo(ivyXmlFile)
        return ivyXmlFile
    }

    private QName ns(String name) {
        return new QName("http://my.extra.info/${name}", name)
    }

    class TestIvyDescriptorFileGenerator extends IvyDescriptorFileGenerator {
        TestIvyDescriptorFileGenerator(IvyPublicationIdentity projectIdentity) {
            super(projectIdentity, false, null)
        }

        TestIvyDescriptorFileGenerator withBranch(String branch) {
            this.branch = branch
            return this
        }

        TestIvyDescriptorFileGenerator withStatus(String status) {
            this.status = status
            return this
        }

        TestIvyDescriptorFileGenerator withAction(Action<XmlProvider> action) {
            this.withXml(action)
            return this
        }

        TestIvyDescriptorFileGenerator withExtraInfo(String name, String value) {
            Map<QName, String> extraInfo = this.getExtraInfo()
            if (extraInfo == null) {
                extraInfo = new LinkedHashMap<QName, String>()
                this.setExtraInfo(extraInfo)
            }
            extraInfo.put(ns(name), value)
            return this
        }
    }
}
