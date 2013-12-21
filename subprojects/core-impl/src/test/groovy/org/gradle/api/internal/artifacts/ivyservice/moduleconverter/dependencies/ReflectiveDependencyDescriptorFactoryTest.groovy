/*
 * Copyright 2013 the original author or authors.
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

import org.apache.ivy.core.module.descriptor.*
import org.apache.ivy.core.module.id.ModuleRevisionId
import spock.lang.Specification

class ReflectiveDependencyDescriptorFactoryTest extends Specification {

    def factory = new ReflectiveDependencyDescriptorFactory()
    def mrid = ModuleRevisionId.newInstance("org.gradle", "gradle-core", "1.0")
    def target = ModuleRevisionId.newInstance("org.gradle", "gradle-core", "2.0")
    def dynamicId = ModuleRevisionId.newInstance("org.gradle", "gradle-core", "latest-integration")

    ModuleDescriptor md = DefaultModuleDescriptor.newBasicInstance(mrid, new Date())

    def "creates updated copy of the descriptor"() {
        def source = new DefaultDependencyDescriptor(md, mrid, dynamicId, true, true, true)

        when:
        def copy = factory.create(source, target)

        then:
        copy.dependencyRevisionId == target

        and:
        copy.force
        copy.changing
        copy.transitive
        copy.dynamicConstraintDependencyRevisionId == dynamicId
        copy.namespace == source.namespace
        copy.sourceModule == source.sourceModule

        and:
        copy.md == source.md
        copy.parentId == source.parentId
        copy.confs == source.confs
        copy.excludeRules == source.excludeRules
        copy.includeRules == source.includeRules
        copy.dependencyArtifacts == source.dependencyArtifacts
    }

    def "correctly configures flags"() {
        def source = new DefaultDependencyDescriptor(md, mrid, dynamicId, false, false, false)

        when:
        def copy = factory.create(source, target)

        then:
        !copy.force
        !copy.changing
        !copy.transitive
    }

    def "correctly configures rules and artifacts"() {
        def source = new DefaultDependencyDescriptor(md, mrid, dynamicId, true, true, false)
        source.addIncludeRule("foo", Mock(IncludeRule))
        source.addExcludeRule("foo", Mock(ExcludeRule))
        source.addDependencyArtifact("foo", Mock(DependencyArtifactDescriptor))

        when:
        def copy = factory.create(source, target)

        then:
        copy.force
        copy.changing
        !copy.transitive

        and:
        copy.confs == source.confs
        copy.excludeRules == source.excludeRules
        copy.includeRules == source.includeRules
        copy.dependencyArtifacts == source.dependencyArtifacts
    }
}
