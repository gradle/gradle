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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.internal.resource.local.LocallyAvailableResource
import spock.lang.Specification

class DisconnectedIvyXmlModuleDescriptorParserTest extends Specification {
    LocallyAvailableExternalResource localExternalResource = Mock()
    LocallyAvailableResource localResource = Mock()
    ExternalResource externalResource = Mock()
    IvyXmlModuleDescriptorParser parser = new DisconnectedIvyXmlModuleDescriptorParser(null, new DefaultImmutableModuleIdentifierFactory())
    DescriptorParseContext parseContext = Mock()

    def "creates overridden internal Ivy parser"() throws Exception {
        when:
        IvyXmlModuleDescriptorParser.Parser disconnectedParser = parser.createParser(parseContext, localExternalResource, [:])

        then:
        localExternalResource.getLocalResource() >> localResource
        localResource.getFile() >> new File('ivy.xml')
        disconnectedParser != null
        disconnectedParser instanceof DisconnectedIvyXmlModuleDescriptorParser.DisconnectedParser
    }

    def "creates new internal Ivy parser"() throws Exception {
        when:
        IvyXmlModuleDescriptorParser.Parser disconnectedParser = parser.createParser(parseContext, localExternalResource, [:])

        and:
        IvyXmlModuleDescriptorParser.Parser newDisconnectedParser = disconnectedParser.newParser(externalResource, new File('ivy.xml').toURI().toURL())

        then:
        localExternalResource.getLocalResource() >> localResource
        localResource.getFile() >> new File('ivy.xml')
        disconnectedParser != null
        disconnectedParser instanceof DisconnectedIvyXmlModuleDescriptorParser.DisconnectedParser
        newDisconnectedParser != null
        newDisconnectedParser instanceof DisconnectedIvyXmlModuleDescriptorParser.DisconnectedParser
    }

    def "parses other Ivy file and return with standard module descriptor"() throws Exception {
        when:
        IvyXmlModuleDescriptorParser.Parser disconnectedParser = parser.createParser(parseContext, localExternalResource, [:])

        and:
        ModuleDescriptor moduleDescriptor = disconnectedParser.parseOtherIvyFile('myorg', 'parentMod', 'parentRev')

        then:
        localExternalResource.getLocalResource() >> localResource
        localResource.getFile() >> new File('ivy.xml')
        disconnectedParser
        disconnectedParser instanceof DisconnectedIvyXmlModuleDescriptorParser.DisconnectedParser
        moduleDescriptor != null
        moduleDescriptor.status == 'release'
        moduleDescriptor.publicationDate != null
        moduleDescriptor.moduleRevisionId.organisation == 'myorg'
        moduleDescriptor.moduleRevisionId.moduleId.name == 'parentMod'
        moduleDescriptor.moduleRevisionId.revision == 'parentRev'
    }
}
