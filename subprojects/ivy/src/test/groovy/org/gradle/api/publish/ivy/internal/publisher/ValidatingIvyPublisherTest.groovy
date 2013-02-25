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

import org.gradle.api.internal.artifacts.repositories.PublicationAwareRepository
import org.gradle.api.publish.ivy.InvalidIvyPublicationException
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyProjectIdentity
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
        group          | name     | version   | message
        ""             | "module" | "version" | "organisation cannot be empty"
        "organisation" | ""       | "version" | "module name cannot be empty"
        "organisation" | "module" | ""        | "revision cannot be empty"
        null           | "module" | "version" | "organisation cannot be empty"
        "organisation" | null     | "version" | "module name cannot be empty"
        "organisation" | "module" | null      | "revision cannot be empty"
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
        t.message == "Invalid publication 'pub-name': artifact ${attribute} cannot be an empty string. Use null instead."

        where:
        attribute |name       |type  | extension | classifier
        "name" | "" | "type" | "ext" | "classifier"
        "type" | "name" | "" | "ext" | "classifier"
        "extension" | "name" | "type" | "" | "classifier"
        "classifier" | "name" | "type" | "ext" | ""
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
        ivyArtifact.file >> theFile

        and:
        def t = thrown InvalidIvyPublicationException
        t.message == "Invalid publication 'pub-name': artifact file ${message}: '${theFile}'"

        where:
        theFile                                                         | message
        new File(testDir.testDirectory, 'does-not-exist') | 'does not exist'
        testDir.testDirectory.createDir('sub_directory')  | 'is a directory'
    }

    private def projectIdentity(def groupId, def artifactId, def version) {
        return Stub(IvyProjectIdentity) {
            getOrganisation() >> groupId
            getModule() >> artifactId
            getRevision() >> version
        }
    }

    private def ivyFile(def group, def moduleName, def version) {
        def ivyXmlFile = testDir.file("ivy")
        IvyDescriptorFileGenerator ivyFileGenerator = new IvyDescriptorFileGenerator(new DefaultIvyProjectIdentity(group, moduleName, version))
        ivyFileGenerator.writeTo(ivyXmlFile)
        return ivyXmlFile
    }
}
