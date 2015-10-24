/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.DefaultModule
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.local.model.DefaultLocalComponentMetaData
import spock.lang.Specification

public class ConfigurationLocalComponentMetaDataAdapterTest extends Specification {
    def componentIdentifierFactory = Mock(ComponentIdentifierFactory)
    def configurationMetaDataBuilder = Mock(ConfigurationComponentMetaDataBuilder)

    ConfigurationLocalComponentMetaDataAdapter resolveModuleDescriptorConverter =
            new ConfigurationLocalComponentMetaDataAdapter(componentIdentifierFactory, configurationMetaDataBuilder)

    def "converts for provided default module"() {
        given:
        def configurations = [Mock(Configuration), Mock(Configuration)] as Set
        def module = new DefaultModule('group-one', 'name-one', 'version-one')

        when:
        def componentMetaData = resolveModuleDescriptorConverter.convert(new ConfigurationBackedComponent(module, configurations))

        then:
        1 * componentIdentifierFactory.createComponentIdentifier(module) >> new DefaultModuleComponentIdentifier('group-one', 'name-one', 'version-one')
        1 * configurationMetaDataBuilder.addConfigurations(!null, configurations)

        and:
        componentMetaData instanceof DefaultLocalComponentMetaData
        componentMetaData.componentId == new DefaultModuleComponentIdentifier('group-one', 'name-one', 'version-one')
    }
}
