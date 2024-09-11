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

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ConfigurationPublications
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.DependencyConstraintSet
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.configurations.DetachedConfigurationsProvider
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.initialization.StandaloneDomainObjectContext
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import org.gradle.util.TestUtil
import spock.lang.Specification

/**
 * Tests {@link DefaultLocalVariantMetadataBuilder}
 */
class DefaultLocalVariantGraphResolveMetadataBuilderTest extends Specification {
    def dependencyMetadataFactory = Mock(DependencyMetadataFactory)
    def excludeRuleConverter = Mock(ExcludeRuleConverter)
    def converter = new DefaultLocalVariantMetadataBuilder(dependencyMetadataFactory, excludeRuleConverter)

    def configuration = Mock(ConfigurationInternal)
    def dependencySet = Mock(DependencySet)
    def dependencyConstraintSet = Mock(DependencyConstraintSet)

    def cache = new LocalVariantMetadataBuilder.DependencyCache();
    def configurationsProvider = new DetachedConfigurationsProvider()
    def componentId = Mock(ComponentIdentifier)

    def setup() {
        ConfigurationPublications outgoing = Mock(ConfigurationPublications)
        outgoing.getCapabilities() >> Collections.emptySet()

        configuration.isCanBeDeclared() >> true
        configuration.name >> "config"
        configuration.extendsFrom >> []
        configuration.hierarchy >> [configuration]
        configuration.outgoing >> outgoing
        configuration.dependencies >> dependencySet
        configuration.dependencyConstraints >> dependencyConstraintSet
        configuration.attributes >> Stub(AttributeContainerInternal)
        configuration.excludeRules >> ([] as Set)
        dependencySet.iterator() >> [].iterator()
        dependencyConstraintSet.iterator() >> [].iterator()

        configurationsProvider.setTheOnlyConfiguration(configuration)
    }

    def "builds configuration with no dependencies or exclude rules"() {
        when:
        def metaData = create()

        then:
        1 * configuration.runDependencyActions()

        metaData.dependencies.size() == 0
        metaData.files.size() == 0
        metaData.excludes.size() == 0
    }

    def "adds ModuleDependency instances from configuration"() {
        def dependencyDescriptor1 = Mock(LocalOriginDependencyMetadata)
        def dependencyDescriptor2 = Mock(LocalOriginDependencyMetadata)
        def dependency1 = Mock(ModuleDependency)
        def dependency2 = Mock(ModuleDependency)

        when:
        def metaData = create()

        then:
        1 * configuration.runDependencyActions()

        when:
        def dependencies = metaData.dependencies

        then:
        1 * dependencySet.iterator() >> [dependency1, dependency2].iterator()
        1 * dependencyMetadataFactory.createDependencyMetadata(dependency1) >> dependencyDescriptor1
        1 * dependencyMetadataFactory.createDependencyMetadata(dependency2) >> dependencyDescriptor2
        dependencies == [dependencyDescriptor1, dependencyDescriptor2]
    }

    def "adds DependencyConstraint instances from configuration"() {
        def dependencyDescriptor1 = Mock(LocalOriginDependencyMetadata)
        def dependencyDescriptor2 = Mock(LocalOriginDependencyMetadata)
        def dependencyConstraint1 = Mock(DependencyConstraint)
        def dependencyConstraint2 = Mock(DependencyConstraint)

        when:
        def metaData = create()

        then:
        1 * configuration.runDependencyActions()

        when:
        def dependencies = metaData.dependencies

        then:
        1 * dependencyConstraintSet.iterator() >> [dependencyConstraint1, dependencyConstraint2].iterator()
        1 * dependencyMetadataFactory.createDependencyConstraintMetadata(dependencyConstraint1) >> dependencyDescriptor1
        1 * dependencyMetadataFactory.createDependencyConstraintMetadata(dependencyConstraint2) >> dependencyDescriptor2
        dependencies == [dependencyDescriptor1, dependencyDescriptor2]
    }

    def "adds FileCollectionDependency instances from configuration"() {
        def dependency1 = Mock(FileCollectionDependency)
        def dependency2 = Mock(FileCollectionDependency)

        when:
        def metaData = create()

        then:
        1 * configuration.runDependencyActions()

        when:
        def files = metaData.files

        then:
        1 * dependencySet.iterator() >> [dependency1, dependency2].iterator()
        files*.source == [dependency1, dependency2]
    }

    def "adds exclude rule from configuration"() {
        def excludeRule = Mock(ExcludeRule)
        ExcludeMetadata ivyExcludeRule = Mock(Exclude)

        when:
        def metaData = create()

        then:
        1 * configuration.runDependencyActions()

        when:
        def excludes = metaData.excludes

        then:
        1 * configuration.excludeRules >> ([excludeRule] as Set)
        1 * excludeRuleConverter.convertExcludeRule(excludeRule) >> ivyExcludeRule
        excludes == ImmutableList.of(ivyExcludeRule)
    }

    def create() {
        return converter.create(configuration, configurationsProvider, componentId, cache, StandaloneDomainObjectContext.ANONYMOUS, TestUtil.calculatedValueContainerFactory())
    }
}
