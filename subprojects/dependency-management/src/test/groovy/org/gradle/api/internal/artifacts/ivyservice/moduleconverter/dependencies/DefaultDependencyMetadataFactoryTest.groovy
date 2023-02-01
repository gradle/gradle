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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyConstraint
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import spock.lang.Specification

class DefaultDependencyMetadataFactoryTest extends Specification {
    def configurationName = "conf"
    def projectDependency = Stub(ProjectDependency)
    def componentId = new ComponentIdentifier() {
        @Override
        String getDisplayName() {
            return "example"
        }
    }

    def "delegates to internal factory"() {
        given:
        def converter1 = Mock(DependencyMetadataConverter)
        def converter2 = Mock(DependencyMetadataConverter)
        def result = Stub(LocalOriginDependencyMetadata)

        when:
        def dependencyDescriptorFactory = new DefaultDependencyMetadataFactory(converter1, converter2)
        def created = dependencyDescriptorFactory.createDependencyMetadata(componentId, configurationName, null, projectDependency)

        then:
        created == result

        and:
        1 * converter1.canConvert(projectDependency) >> false
        1 * converter2.canConvert(projectDependency) >> true
        1 * converter2.createDependencyMetadata(componentId, configurationName, null, projectDependency) >> result
    }

    def "fails where no internal factory can handle dependency type"() {
        def converter = Mock(DependencyMetadataConverter);

        when:
        converter.canConvert(projectDependency) >> false

        and:
        def dependencyDescriptorFactory = new DefaultDependencyMetadataFactory(converter)
        dependencyDescriptorFactory.createDependencyMetadata(componentId, configurationName, null, projectDependency)

        then:
        thrown InvalidUserDataException
    }

    def "creates descriptor for dependency constraints"() {
        given:
        def dependencyConstraint = new DefaultDependencyConstraint("g", "m", "1")

        when:
        def dependencyDescriptorFactory = new DefaultDependencyMetadataFactory()
        def created = dependencyDescriptorFactory.createDependencyConstraintMetadata(componentId, configurationName, null, dependencyConstraint)
        def selector = created.selector as ModuleComponentSelector

        then:
        created.constraint
        selector.group == "g"
        selector.module == "m"
        selector.version == "1"

        and:
        created.moduleConfiguration == configurationName
        created.artifacts.empty
        created.excludes.empty
        !created.force
        !created.transitive
        !created.changing
    }
}
