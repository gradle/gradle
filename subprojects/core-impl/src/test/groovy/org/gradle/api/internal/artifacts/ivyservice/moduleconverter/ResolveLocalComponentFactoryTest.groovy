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
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.DefaultModule
import org.gradle.api.internal.artifacts.ProjectBackedModule
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier
import org.gradle.api.internal.artifacts.component.DefaultProjectComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependenciesToModuleDescriptorConverter
import org.gradle.api.internal.artifacts.metadata.DefaultLocalComponentMetaData
import spock.lang.Specification

public class ResolveLocalComponentFactoryTest extends Specification {

    def "converts for provided default module"() {
        given:
        def configurations = [Mock(Configuration), Mock(Configuration)] as Set
        def module = new DefaultModule('group-one', 'name-one', 'version-one')
        def moduleDescriptor = Mock(DefaultModuleDescriptor)
        def moduleDescriptorFactory = Mock(ModuleDescriptorFactory)
        def configurationsConverter = Mock(ConfigurationsToModuleDescriptorConverter)
        def dependenciesConverter = Mock(DependenciesToModuleDescriptorConverter)

        ResolveLocalComponentFactory resolveModuleDescriptorConverter = new ResolveLocalComponentFactory(
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
        actualDescriptor instanceof DefaultLocalComponentMetaData
        actualDescriptor.moduleDescriptor == moduleDescriptor
        actualDescriptor.toResolveMetaData().componentId == new DefaultModuleComponentIdentifier('group-one', 'name-one', 'version-one')
    }

    def "converts for provided project backed module"() {
        given:
        def configurations = [Mock(Configuration), Mock(Configuration)] as Set
        def project = Mock(Project)
        def module = new ProjectBackedModule(project)
        def moduleDescriptor = Mock(DefaultModuleDescriptor)
        def moduleDescriptorFactory = Mock(ModuleDescriptorFactory)
        def configurationsConverter = Mock(ConfigurationsToModuleDescriptorConverter)
        def dependenciesConverter = Mock(DependenciesToModuleDescriptorConverter)

        ResolveLocalComponentFactory resolveModuleDescriptorConverter = new ResolveLocalComponentFactory(
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
        1 * project.path >> ':myPath'

        and:
        actualDescriptor instanceof DefaultLocalComponentMetaData
        actualDescriptor.moduleDescriptor == moduleDescriptor
        actualDescriptor.toResolveMetaData().componentId == new DefaultProjectComponentIdentifier(':myPath')
    }
}
