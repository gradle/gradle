/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine

import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.resolve.ResolveData
import org.apache.ivy.core.resolve.ResolveEngine
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.resolve.ResolvedModuleRevision
import org.gradle.api.artifacts.LenientConfiguration
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.DefaultResolvedModuleId
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.ivyservice.ArtifactToFileResolver
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleResolver
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter
import org.gradle.api.internal.artifacts.ivyservice.ResolvedArtifactFactory
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.EnhancedDependencyDescriptor
import org.gradle.api.specs.Spec
import spock.lang.Specification

import org.apache.ivy.core.module.descriptor.DefaultExcludeRule
import org.apache.ivy.plugins.matcher.ExactPatternMatcher
import org.apache.ivy.plugins.matcher.PatternMatcher
import org.apache.ivy.core.module.id.ArtifactId

class DependencyGraphBuilderTest extends Specification {
    final ModuleDescriptorConverter moduleDescriptorConverter = Mock()
    final ResolvedArtifactFactory resolvedArtifactFactory = Mock()
    final ConfigurationInternal configuration = Mock()
    final ResolveEngine resolveEngine = Mock()
    final ResolveData resolveData = new ResolveData(resolveEngine, new ResolveOptions())
    final ModuleConflictResolver conflictResolver = Mock()
    final DependencyToModuleResolver dependencyResolver = Mock()
    final ArtifactToFileResolver artifactResolver = Mock()
    final DefaultModuleDescriptor root = revision('root')
    final DependencyGraphBuilder builder = new DependencyGraphBuilder(moduleDescriptorConverter, resolvedArtifactFactory, artifactResolver, dependencyResolver, conflictResolver)

    def setup() {
        config(root, 'root', 'default')
        _ * configuration.name >> 'root'
        _ * moduleDescriptorConverter.convert(_, _) >> root
    }

    def "includes dependencies of selected module on conflict when module already traversed"() {
        given:
        def a1 = revision('a', '1.2')
        def a2 = revision('a', '1.1')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        def e = revision('e')
        traverses root, a1
        traverses a1, c
        traverses root, b
        traverses b, d
        traverses d, a2 // Conflict is deeper than all dependencies of other module
        doesNotTraverse a2, e

        when:
        def result = builder.resolve(configuration, resolveData)

        then:
        1 * conflictResolver.select(!null, !null) >> { Set<ModuleRevisionState> candidates, ModuleRevisionState root ->
            assert candidates*.revision == ['1.2', '1.1']
            return candidates.find { it.revision == '1.2' }
        }
        0 * conflictResolver._

        and:
        modules(result) == ids(a1, b, c, d)
    }

    def "includes dependencies of selected module on conflict when other module already traversed"() {
        given:
        def a1 = revision('a', '1.2')
        def a2 = revision('a', '1.1')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        def e = revision('e')
        traverses root, a2
        traverses a2, c
        traverses root, b
        traverses b, d
        traverses d, a1 // Conflict is deeper than all dependencies of other module
        traverses a1, e

        when:
        def result = builder.resolve(configuration, resolveData)

        then:
        1 * conflictResolver.select(!null, !null) >> { Set<ModuleRevisionState> candidates, ModuleRevisionState root ->
            assert candidates*.revision == ['1.1', '1.2']
            return candidates.find { it.revision == '1.2' }
        }
        0 * conflictResolver._

        and:
        modules(result) == ids(a1, b, d, e)
    }

    def "includes dependencies of selected module on conflict when path through other module queued for traversal"() {
        given:
        def a1 = revision('a', '1.2')
        def a2 = revision('a', '1.1')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        traverses root, b
        traverses b, a2
        doesNotTraverse a2, c
        traverses root, a1
        traverses a1, d

        when:
        def result = builder.resolve(configuration, resolveData)

        then:
        1 * conflictResolver.select(!null, !null) >> { Set<ModuleRevisionState> candidates, ModuleRevisionState root ->
            assert candidates*.revision == ['1.2', '1.1']
            return candidates.find { it.revision == '1.2' }
        }
        0 * conflictResolver._

        and:
        modules(result) == ids(a1, b, d)
    }

    def "restarts conflict resolution when later conflict discovered"() {
        given:
        def a1 = revision('a', '1.2')
        def a2 = revision('a', '1.1')
        def a3 = revision('a', '1.0')
        def b1 = revision('b', '2.2')
        def b2 = revision('b', '2.1')
        def c = revision('c')
        traverses root, a2
        traverses root, a1
        traverses a1, c
        traverses root, b2
        traverses root, b1
        traverses b1, a3

        when:
        def result = builder.resolve(configuration, resolveData)

        then:
        1 * conflictResolver.select({it*.revision == ['1.1', '1.2']}, !null) >> { Set<ModuleRevisionState> candidates, ModuleRevisionState root ->
            return candidates.find { it.revision == '1.2' }
        }
        1 * conflictResolver.select({it*.revision == ['2.1', '2.2']}, !null) >> { Set<ModuleRevisionState> candidates, ModuleRevisionState root ->
            return candidates.find { it.revision == '2.2' }
        }
        1 * conflictResolver.select({it*.revision == ['1.1', '1.2', '1.0']}, !null) >> { Set<ModuleRevisionState> candidates, ModuleRevisionState root ->
            return candidates.find { it.revision == '1.2' }
        }
        0 * conflictResolver._

        and:
        modules(result) == ids(a1, c, b1)

    }

    def "does not attempt to resolve a dependency whose target module is excluded earlier in the path"() {
        given:
        def a = revision('a')
        def b = revision('b')
        def c = revision('c')
        traverses root, a
        traverses a, b, exclude: c
        doesNotTraverse b, c

        when:
        def result = builder.resolve(configuration, resolveData)

        then:
        modules(result) == ids(a, b)
    }

    def revision(String name, String revision = '1.0') {
        DefaultModuleDescriptor descriptor = new DefaultModuleDescriptor(new ModuleRevisionId(new ModuleId("group", name), revision), "release", new Date())
        config(descriptor, 'default')
        return descriptor
    }

    def config(DefaultModuleDescriptor descriptor, String name, String... extendsFrom) {
        def configuration = new Configuration(name, Configuration.Visibility.PUBLIC, null, extendsFrom, true, null)
        descriptor.addConfiguration(configuration)
        return configuration
    }

    def traverses(Map<String, ?> args = [:], DefaultModuleDescriptor from, DefaultModuleDescriptor to) {
        def descriptor = dependsOn(args, from, to)
        1 * dependencyResolver.resolve(descriptor) >> new ResolvedModuleRevision(null, null, to, null)
    }

    def doesNotTraverse(Map<String, ?> args = [:], DefaultModuleDescriptor from, DefaultModuleDescriptor to) {
        def descriptor = dependsOn(args, from, to)
        0 * dependencyResolver.resolve(descriptor)
    }

    def dependsOn(Map<String, ?> args = [:], DefaultModuleDescriptor from, DefaultModuleDescriptor to) {
        ModuleDependency moduleDependency = Mock()
        def descriptor = new EnhancedDependencyDescriptor(moduleDependency, from, to.moduleRevisionId, false, false, true)
        descriptor.addDependencyConfiguration("default", "default")
        if (args.exclude) {
            descriptor.addExcludeRule("default", new DefaultExcludeRule(new ArtifactId(
                    args.exclude.moduleRevisionId.moduleId, PatternMatcher.ANY_EXPRESSION,
                    PatternMatcher.ANY_EXPRESSION,
                    PatternMatcher.ANY_EXPRESSION),
                    ExactPatternMatcher.INSTANCE, null))
        }
        from.addDependency(descriptor)
        return descriptor
    }

    def ids(ModuleDescriptor... descriptors) {
        return descriptors.collect { new DefaultResolvedModuleId(it.moduleRevisionId.organisation, it.moduleRevisionId.name, it.moduleRevisionId.revision)} as Set
    }

    def modules(LenientConfiguration config) {
        config.rethrowFailure()
        Set<ModuleIdentifier> result = new LinkedHashSet<ModuleIdentifier>()
        List<ResolvedDependency> queue = []
        queue.addAll(config.getFirstLevelModuleDependencies({true} as Spec))
        while (!queue.empty) {
            def node = queue.remove(0)
            result.add(node.module.id)
            queue.addAll(0, node.children)
        }
        return result
    }
}
