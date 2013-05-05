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
import org.gradle.api.internal.artifacts.repositories.PublicationAwareRepository
import org.gradle.api.publish.ivy.InvalidIvyPublicationException
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.internal.artifact.DefaultIvyArtifact
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static java.util.Collections.emptySet
import static org.gradle.util.CollectionUtils.toSet

public class ValidatingIvyPublisherTest extends Specification {
    @Shared TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider()
    def delegate = Mock(IvyPublisher)
    def publisher = new ValidatingIvyPublisher(delegate)

    def "delegates when publication is valid"() {
        when:
        def projectIdentity = this.projectIdentity("the-group", "the-artifact", "the-version")
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity, ivyFile("the-group", "the-artifact", "the-version"), emptySet())
        def repository = Mock(PublicationAwareRepository)

        and:
        publisher.publish(publication, repository)

        then:
        delegate.publish(publication, repository)
    }

    def "does not attempt to resolve extended ivy descriptor when validating"() {
        when:
        def ivyFile = ivyFile("the-group", "the-artifact", "the-version", new Action<XmlProvider>() {
            void execute(XmlProvider t) {
                t.asNode().info[0].appendNode("extends", ["organisation": "parent-org", "module": "parent-module", "revision": "parent-revision"])
            }
        })

        and:
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity("the-group", "the-artifact", "the-version"), ivyFile, emptySet())
        def repository = Mock(PublicationAwareRepository)

        and:
        publisher.publish(publication, repository)

        then:
        delegate.publish(publication, repository)
    }

    def "validates project coordinates"() {
        given:
        def projectIdentity = projectIdentity(group, name, version)
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity, ivyFile(group, name, version), emptySet())
        def repository = Mock(PublicationAwareRepository)

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

    def "project coordinates must match ivy descriptor file"() {
        given:
        def projectIdentity = projectIdentity("org", "module", "version")
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity, ivyFile(organisation, module, version), emptySet())
        def repository = Mock(PublicationAwareRepository)

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

    def "reports and fails with invalid descriptor file"() {
        given:
        IvyDescriptorFileGenerator ivyFileGenerator = new IvyDescriptorFileGenerator(new DefaultIvyPublicationIdentity("the-group", "the-artifact", "the-version"))
        final artifact = new DefaultIvyArtifact(null, "name", "ext", "type", "classifier")
        artifact.setConf("unknown")
        ivyFileGenerator.addArtifact(artifact)

        def ivyFile = testDir.file("ivy")
        ivyFileGenerator.writeTo(ivyFile)

        and:
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity("the-group", "the-artifact", "the-version"), ivyFile, emptySet())
        def repository = Mock(PublicationAwareRepository)

        when:
        publisher.publish(publication, repository)

        then:
        def e = thrown InvalidIvyPublicationException
        e.message.startsWith "Invalid publication 'pub-name': Problem occurred while parsing ivy file: " +
                "Cannot add artifact 'name.ext(type)' to configuration 'unknown' of module the-group#the-artifact;the-version because this configuration doesn't exist!"
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
        publisher.publish(publication, Mock(PublicationAwareRepository))

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

        when:
        publisher.publish(publication, Mock(PublicationAwareRepository))

        then:
        ivyArtifact.name >> "name"
        ivyArtifact.type >> "type"
        ivyArtifact.extension >> "ext"
        ivyArtifact.file >> theFile

        and:
        def t = thrown InvalidIvyPublicationException
        t.message == "Invalid publication 'pub-name': artifact file ${message}: '${theFile}'"

        where:
        theFile                                                         | message
        new File(testDir.testDirectory, 'does-not-exist') | 'does not exist'
        testDir.testDirectory.createDir('sub_directory')  | 'is a directory'
    }

    def "cannot publish with duplicate artifacts"() {
        given:
        IvyArtifact artifact1 = Stub() {
            getName() >> "name"
            getExtension() >> "ext1"
            getType() >> "type"
            getClassifier() >> "classified"
            getFile() >> testDir.createFile('artifact1')
        }
        IvyArtifact artifact2 = Stub() {
            getName() >> "name"
            getExtension() >> "ext1"
            getType() >> "type"
            getClassifier() >> "classified"
            getFile() >> testDir.createFile('artifact2')
        }
        def projectIdentity = projectIdentity("org", "module", "revision")
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity, ivyFile("org", "module", "revision"), toSet([artifact1, artifact2]))

        when:
        publisher.publish(publication, Mock(PublicationAwareRepository))

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
            getFile() >> testDir.createFile('artifact1')
        }
        def projectIdentity = projectIdentity("org", "module", "revision")
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity, ivyFile("org", "module", "revision"), toSet([artifact1]))

        when:
        publisher.publish(publication, Mock(PublicationAwareRepository))

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

    private def ivyFile(def group, def moduleName, def version, Action<XmlProvider> action = null) {
        def ivyXmlFile = testDir.file("ivy")
        IvyDescriptorFileGenerator ivyFileGenerator = new IvyDescriptorFileGenerator(new DefaultIvyPublicationIdentity(group, moduleName, version))
        if (action != null) {
            ivyFileGenerator.withXml(action)
        }
        ivyFileGenerator.writeTo(ivyXmlFile)
        return ivyXmlFile
    }
}
