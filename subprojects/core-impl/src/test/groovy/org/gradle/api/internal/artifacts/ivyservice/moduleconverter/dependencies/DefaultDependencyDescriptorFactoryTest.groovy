/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ProjectDependency
import spock.lang.Specification

public class DefaultDependencyDescriptorFactoryTest extends Specification {
    def configurationName = "conf";
    def moduleDescriptor = Mock(DefaultModuleDescriptor);
    def projectDependency = Mock(ProjectDependency);
    def dependencyDescriptor = Mock(EnhancedDependencyDescriptor)

    def "delegates to internal factory"() {
        given:
        def ivyDependencyDescriptorFactory1 = Mock(IvyDependencyDescriptorFactory);
        def ivyDependencyDescriptorFactory2 = Mock(IvyDependencyDescriptorFactory);

        when:
        1 * ivyDependencyDescriptorFactory1.canConvert(projectDependency) >> false
        1 * ivyDependencyDescriptorFactory2.canConvert(projectDependency) >> true
        1 * ivyDependencyDescriptorFactory2.createDependencyDescriptor(configurationName, projectDependency, moduleDescriptor) >> dependencyDescriptor
        1 * moduleDescriptor.addDependency(dependencyDescriptor)

        then:
        DefaultDependencyDescriptorFactory dependencyDescriptorFactory = new DefaultDependencyDescriptorFactory(
                ivyDependencyDescriptorFactory1, ivyDependencyDescriptorFactory2
        );
        dependencyDescriptorFactory.addDependencyDescriptor(configurationName, moduleDescriptor, projectDependency);
    }

    def "fails where no internal factory can handle dependency type"() {
        def ivyDependencyDescriptorFactory1 = Mock(IvyDependencyDescriptorFactory);

        when:
        ivyDependencyDescriptorFactory1.canConvert(projectDependency) >> false

        and:
        DefaultDependencyDescriptorFactory dependencyDescriptorFactory = new DefaultDependencyDescriptorFactory(
                ivyDependencyDescriptorFactory1
        );
        dependencyDescriptorFactory.addDependencyDescriptor(configurationName, moduleDescriptor, projectDependency);

        then:
        thrown InvalidUserDataException
    }
}
