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

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.ModuleInternal
import org.gradle.api.internal.artifacts.ivyservice.LocalComponentFactory
import org.gradle.api.internal.artifacts.metadata.MutableLocalComponentMetaData
import spock.lang.Specification

public class PublishLocalComponentFactoryTest extends Specification {

    def "converts"() {
        given:
        def configurationsDummy = [Mock(Configuration)] as Set
        def moduleDummy = Mock(ModuleInternal)
        def moduleDescriptorDummy = Mock(DefaultModuleDescriptor)
        def componentMetaDataDummy = Mock(MutableLocalComponentMetaData)
        def artifactsToModuleDescriptorConverter = Mock(ConfigurationsToArtifactsConverter)
        def resolveModuleDescriptorConverter = Mock(LocalComponentFactory)

        def publishModuleDescriptorConverter = new PublishLocalComponentFactory(
                resolveModuleDescriptorConverter,
                artifactsToModuleDescriptorConverter);

        and:
        componentMetaDataDummy.moduleDescriptor >> moduleDescriptorDummy
        resolveModuleDescriptorConverter.convert(configurationsDummy, moduleDummy) >> componentMetaDataDummy

        when:
        def actualMetaData = publishModuleDescriptorConverter.convert(configurationsDummy, moduleDummy);

        then:
        1 * moduleDescriptorDummy.addExtraAttributeNamespace(PublishLocalComponentFactory.IVY_MAVEN_NAMESPACE_PREFIX,
                    PublishLocalComponentFactory.IVY_MAVEN_NAMESPACE);
        1 * artifactsToModuleDescriptorConverter.addArtifacts(componentMetaDataDummy, configurationsDummy)

        and:
        actualMetaData == componentMetaDataDummy
    }
}
