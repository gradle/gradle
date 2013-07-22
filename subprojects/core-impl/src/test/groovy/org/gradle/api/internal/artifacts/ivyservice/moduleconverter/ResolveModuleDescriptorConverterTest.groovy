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
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Module
import org.gradle.api.internal.artifacts.DefaultModuleVersionPublishMetaData
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependenciesToModuleDescriptorConverter
import spock.lang.Specification

public class ResolveModuleDescriptorConverterTest extends Specification {

    def "converts"() {
        given:
        def configurations = [Mock(Configuration), Mock(Configuration)] as Set
        def module = Mock(Module)
        def moduleDescriptor = Mock(DefaultModuleDescriptor)
        def moduleDescriptorFactory = Mock(ModuleDescriptorFactory)
        def configurationsConverter = Mock(ConfigurationsToModuleDescriptorConverter)
        def dependenciesConverter = Mock(DependenciesToModuleDescriptorConverter)

        ResolveModuleDescriptorConverter resolveModuleDescriptorConverter = new ResolveModuleDescriptorConverter(
                moduleDescriptorFactory,
                configurationsConverter,
                dependenciesConverter);

        and:
        moduleDescriptor.moduleRevisionId >> ModuleRevisionId.newInstance("group", "module", "version")

        when:
        def actualDescriptor = resolveModuleDescriptorConverter.convert(configurations, module);

        then:
        1 * moduleDescriptorFactory.createModuleDescriptor(module) >> moduleDescriptor
        1 * configurationsConverter.addConfigurations(moduleDescriptor, configurations)
        1 * dependenciesConverter.addDependencyDescriptors(moduleDescriptor, configurations)

        and:
        actualDescriptor instanceof DefaultModuleVersionPublishMetaData
        actualDescriptor.moduleDescriptor == moduleDescriptor
    }
}
