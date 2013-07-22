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
import org.gradle.api.artifacts.Module
import org.gradle.api.internal.artifacts.BuildableModuleVersionPublishMetaData
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter
import spock.lang.Specification

public class PublishModuleDescriptorConverterTest extends Specification {

    def "converts"() {
        given:
        def configurationsDummy = [Mock(Configuration)] as Set
        def moduleDummy = Mock(Module)
        def moduleDescriptorDummy = Mock(DefaultModuleDescriptor)
        def publishMetaDataDummy = Mock(BuildableModuleVersionPublishMetaData)
        def artifactsToModuleDescriptorConverter = Mock(ArtifactsToModuleDescriptorConverter)
        def resolveModuleDescriptorConverter = Mock(ModuleDescriptorConverter)

        def publishModuleDescriptorConverter = new PublishModuleDescriptorConverter(
                resolveModuleDescriptorConverter,
                artifactsToModuleDescriptorConverter);

        and:
        publishMetaDataDummy.moduleDescriptor >> moduleDescriptorDummy
        resolveModuleDescriptorConverter.convert(configurationsDummy, moduleDummy) >> publishMetaDataDummy

        when:
        def actualMetaData = publishModuleDescriptorConverter.convert(configurationsDummy, moduleDummy);

        then:
        1 * moduleDescriptorDummy.addExtraAttributeNamespace(PublishModuleDescriptorConverter.IVY_MAVEN_NAMESPACE_PREFIX,
                    PublishModuleDescriptorConverter.IVY_MAVEN_NAMESPACE);
        1 * artifactsToModuleDescriptorConverter.addArtifacts(publishMetaDataDummy, configurationsDummy)

        and:
        actualMetaData == publishMetaDataDummy
    }
}
