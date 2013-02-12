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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Module
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.internal.artifacts.DefaultModule
import org.gradle.api.publish.ivy.internal.tasks.IvyDescriptorFileGenerator
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification

public class ValidatingIvyPublisherTest extends Specification {
    TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider()
    def delegate = Mock(IvyPublisher)
    def publisher = new ValidatingIvyPublisher(delegate)

    def "delegates when publication is valid"() {
        when:
        def module = module("the-group", "the-artifact", "the-version")
        def publication = new IvyNormalizedPublication("pub-name",  module, Collections.emptySet(), createIvyXmlFile("the-group", "the-artifact", "the-version"))
        def repository = Mock(IvyArtifactRepository)

        and:
        publisher.publish(publication, repository)

        then:
        delegate.publish(publication, repository)
    }

    def "validates project coordinates"() {
        given:
        def projectIdentity = module(group, name, version)
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity, Collections.emptySet(), createIvyXmlFile(group, name, version))
        def repository = Mock(IvyArtifactRepository)

        when:
        publisher.publish(publication, repository)

        then:
        def e = thrown InvalidUserDataException
        e.message == message

        where:
        group          | name     | version   | message
        ""             | "module" | "version" | "The organisation value cannot be empty"
        "organisation" | ""       | "version" | "The module name value cannot be empty"
        "organisation" | "module" | ""        | "The revision value cannot be empty"
    }

    def "project coordinates must match ivy descriptor file"() {
        given:
        def projectIdentity = module("org", "module", "version")
        def publication = new IvyNormalizedPublication("pub-name", projectIdentity, Collections.emptySet(), createIvyXmlFile(organisation, module, version))
        def repository = Mock(IvyArtifactRepository)

        when:
        publisher.publish(publication, repository)

        then:
        def e = thrown InvalidUserDataException
        e.message == message

        where:
        organisation | module       | version       | message
        "org-mod"    | "module"     | "version"     | "Publication organisation does not match ivy descriptor. Cannot edit organisation directly in the ivy descriptor file."
        "org"        | "module-mod" | "version"     | "Publication module name does not match ivy descriptor. Cannot edit module name directly in the ivy descriptor file."
        "org"        | "module"     | "version-mod" | "Publication revision does not match ivy descriptor. Cannot edit revision directly in the ivy descriptor file."
    }

    private def module(def groupId, def artifactId, def version) {
        return Stub(Module) {
            getGroup() >> groupId
            getName() >> artifactId
            getVersion() >> version
        }
    }

    private def createIvyXmlFile(def group, def moduleName, def version) {
        def ivyXmlFile = testDir.file("ivy")
        IvyDescriptorFileGenerator ivyFileGenerator = new IvyDescriptorFileGenerator(new DefaultModule(group, moduleName, version))
        ivyFileGenerator.writeTo(ivyXmlFile)
        return ivyXmlFile
    }
}
