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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ExcludeRuleConverter
import org.gradle.internal.component.model.DependencyMetaData
import org.gradle.internal.component.local.model.BuildableLocalComponentMetaData
import spock.lang.Specification

import static org.gradle.util.WrapUtil.toDomainObjectSet

public class DefaultDependenciesToModuleDescriptorConverterTest extends Specification {

    def dependencyDescriptorFactory = Mock(DependencyDescriptorFactory)
    def excludeRuleConverter = Mock(ExcludeRuleConverter)
    def converter = new DefaultDependenciesToModuleDescriptorConverter(dependencyDescriptorFactory, excludeRuleConverter)

    def descriptor = Mock(DefaultModuleDescriptor)
    def metaData = Mock(BuildableLocalComponentMetaData)
    def configuration = Mock(Configuration)
    def dependencySet = Mock(DependencySet.class);

    def "ignores configuration with no dependencies or exclude rules"() {
        when:
        converter.addDependencyDescriptors(metaData, [configuration])

        then:
        1 * configuration.dependencies >> dependencySet
        1 * dependencySet.withType(ModuleDependency) >> toDomainObjectSet(ModuleDependency)
        1 * configuration.excludeRules >> ([] as Set)
        0 * _
    }

    def "adds dependencies from configuration"() {
        def dependencyDescriptor = Mock(DependencyMetaData)
        def dependency = Mock(ModuleDependency)

        when:
        converter.addDependencyDescriptors(metaData, [configuration])

        then:
        _ * metaData.moduleDescriptor >> descriptor
        1 * configuration.dependencies >> dependencySet
        1 * dependencySet.withType(ModuleDependency) >> toDomainObjectSet(ModuleDependency, dependency)
        1 * configuration.name >> "config"
        1 * dependencyDescriptorFactory.createDependencyDescriptor("config", dependency) >> dependencyDescriptor
        1 * metaData.addDependency(dependencyDescriptor)
        1 * configuration.excludeRules >> ([] as Set)
        0 * _
    }

    def "adds exclude rule from configuration"() {
        def excludeRule = Mock(ExcludeRule)
        def ivyExcludeRule = Mock(org.apache.ivy.core.module.descriptor.ExcludeRule)

        when:
        converter.addDependencyDescriptors(metaData, [configuration])

        then:
        1 * configuration.dependencies >> dependencySet
        1 * dependencySet.withType(ModuleDependency) >> toDomainObjectSet(ModuleDependency)

        1 * configuration.excludeRules >> ([excludeRule] as Set)
        1 * configuration.getName() >> "config"
        1 * excludeRuleConverter.createExcludeRule("config", excludeRule) >> ivyExcludeRule
        1 * metaData.addExcludeRule(ivyExcludeRule)
        0 * _
    }
}
