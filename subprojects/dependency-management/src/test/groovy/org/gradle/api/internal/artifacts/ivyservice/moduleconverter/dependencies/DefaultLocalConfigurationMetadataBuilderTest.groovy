/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.DependencyConstraintSet
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.internal.component.local.model.BuildableLocalConfigurationMetadata
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import spock.lang.Specification

class DefaultLocalConfigurationMetadataBuilderTest extends Specification {
    def dependencyDescriptorFactory = Mock(DependencyDescriptorFactory)
    def excludeRuleConverter = Mock(ExcludeRuleConverter)
    def converter = new DefaultLocalConfigurationMetadataBuilder(dependencyDescriptorFactory, excludeRuleConverter)

    def descriptor = Mock(DefaultModuleDescriptor)
    def metaData = Mock(BuildableLocalConfigurationMetadata)
    def configuration = Mock(ConfigurationInternal)
    def dependencySet = Mock(DependencySet)
    def dependencyConstraintSet = Mock(DependencyConstraintSet)

    def "ignores configuration with no dependencies or exclude rules"() {
        when:
        converter.addDependenciesAndExcludes(metaData, configuration)

        then:
        1 * configuration.runDependencyActions()
        1 * configuration.dependencies >> dependencySet
        1 * configuration.dependencyConstraints >> dependencyConstraintSet
        1 * dependencySet.iterator() >> [].iterator()
        1 * dependencyConstraintSet.iterator() >> [].iterator()
        1 * configuration.excludeRules >> ([] as Set)
        2 * configuration.attributes >> Stub(AttributeContainerInternal)
        0 * _
    }

    def "adds ModuleDependency instances from configuration"() {
        def componentId = Mock(ComponentIdentifier)
        def dependencyDescriptor1 = Mock(LocalOriginDependencyMetadata)
        def dependencyDescriptor2 = Mock(LocalOriginDependencyMetadata)
        def dependency1 = Mock(ModuleDependency)
        def dependency2 = Mock(ModuleDependency)

        when:
        converter.addDependenciesAndExcludes(metaData, configuration)

        then:
        _ * metaData.getComponentId() >> componentId
        1 * configuration.runDependencyActions()
        1 * configuration.dependencies >> dependencySet
        1 * configuration.dependencyConstraints >> dependencyConstraintSet
        1 * dependencySet.iterator() >> [dependency1, dependency2].iterator()
        1 * dependencyConstraintSet.iterator() >> [].iterator()
        _ * configuration.name >> "config"
        _ * configuration.attributes >> Stub(AttributeContainerInternal)
        1 * dependencyDescriptorFactory.createDependencyDescriptor(componentId, "config", _, dependency1) >> dependencyDescriptor1
        1 * dependencyDescriptorFactory.createDependencyDescriptor(componentId, "config", _, dependency2) >> dependencyDescriptor2
        1 * metaData.addDependency(dependencyDescriptor1)
        1 * metaData.addDependency(dependencyDescriptor2)
        1 * configuration.excludeRules >> ([] as Set)
        0 * _
    }

    def "adds DependencyConstraint instances from configuration"() {
        def componentId = Mock(ComponentIdentifier)
        def dependencyDescriptor1 = Mock(LocalOriginDependencyMetadata)
        def dependencyDescriptor2 = Mock(LocalOriginDependencyMetadata)
        def dependencyConstraint1 = Mock(DependencyConstraint)
        def dependencyConstraint2 = Mock(DependencyConstraint)

        when:
        converter.addDependenciesAndExcludes(metaData, configuration)

        then:
        _ * metaData.getComponentId() >> componentId
        1 * configuration.runDependencyActions()
        1 * configuration.dependencies >> dependencySet
        1 * configuration.dependencyConstraints >> dependencyConstraintSet
        1 * dependencySet.iterator() >> [].iterator()
        1 * dependencyConstraintSet.iterator() >> [dependencyConstraint1, dependencyConstraint2].iterator()
        _ * configuration.name >> "config"
        _ * configuration.attributes >> Stub(AttributeContainerInternal)
        1 * dependencyDescriptorFactory.createDependencyConstraintDescriptor(componentId, "config", _, dependencyConstraint1) >> dependencyDescriptor1
        1 * dependencyDescriptorFactory.createDependencyConstraintDescriptor(componentId, "config", _, dependencyConstraint2) >> dependencyDescriptor2
        1 * metaData.addDependency(dependencyDescriptor1)
        1 * metaData.addDependency(dependencyDescriptor2)
        1 * configuration.excludeRules >> ([] as Set)
        0 * _
    }

    def "adds FileCollectionDependency instances from configuration"() {
        def dependency1 = Mock(FileCollectionDependency)
        def dependency2 = Mock(FileCollectionDependency)

        when:
        converter.addDependenciesAndExcludes(metaData, configuration)

        then:
        1 * configuration.runDependencyActions()
        1 * configuration.dependencies >> dependencySet
        1 * configuration.dependencyConstraints >> dependencyConstraintSet
        1 * dependencySet.iterator() >> [dependency1, dependency2].iterator()
        1 * dependencyConstraintSet.iterator() >> [].iterator()
        _ * configuration.name >> "config"
        _ * configuration.attributes >> Stub(AttributeContainerInternal)
        1 * metaData.addFiles({it.source == dependency1})
        1 * metaData.addFiles({it.source == dependency2})
        1 * configuration.excludeRules >> ([] as Set)
        0 * _
    }

    def "adds exclude rule from configuration"() {
        def excludeRule = Mock(ExcludeRule)
        def ivyExcludeRule = Mock(Exclude)

        when:
        converter.addDependenciesAndExcludes(metaData, configuration)

        then:
        1 * configuration.runDependencyActions()
        1 * configuration.dependencies >> dependencySet
        1 * configuration.dependencyConstraints >> dependencyConstraintSet
        1 * dependencySet.iterator() >> [].iterator()
        1 * dependencyConstraintSet.iterator() >> [].iterator()

        1 * configuration.excludeRules >> ([excludeRule] as Set)
        _ * configuration.getName() >> "config"
        _ * configuration.attributes >> Stub(AttributeContainerInternal)
        1 * excludeRuleConverter.convertExcludeRule(excludeRule) >> ivyExcludeRule
        1 * metaData.addExclude(ivyExcludeRule)
        0 * _
    }
}
