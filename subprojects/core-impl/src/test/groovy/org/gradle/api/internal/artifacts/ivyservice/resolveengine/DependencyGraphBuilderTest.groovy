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

import org.apache.ivy.core.module.descriptor.*
import org.apache.ivy.core.module.id.ArtifactId
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.resolve.ResolveData
import org.apache.ivy.core.resolve.ResolveEngine
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.plugins.matcher.ExactPatternMatcher
import org.apache.ivy.plugins.matcher.PatternMatcher
import org.gradle.api.artifacts.*
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.ivyservice.*
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DefaultBuildableModuleVersionMetaData
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionMetaData
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.EnhancedDependencyDescriptor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolvedConfigurationListener
import org.gradle.api.specs.Spec
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons

class DependencyGraphBuilderTest extends Specification {
    final ModuleDescriptorConverter moduleDescriptorConverter = Mock()
    final ResolvedArtifactFactory resolvedArtifactFactory = Mock()
    final ConfigurationInternal configuration = Mock()
    final ResolveEngine resolveEngine = Mock()
    final ResolveData resolveData = new ResolveData(resolveEngine, new ResolveOptions())
    final ModuleConflictResolver conflictResolver = Mock()
    final DependencyToModuleVersionIdResolver dependencyResolver = Mock()
    final ResolvedConfigurationListener listener = Mock()
    final ModuleVersionMetaData root = revision('root')
    final DependencyGraphBuilder builder = new DependencyGraphBuilder(moduleDescriptorConverter, resolvedArtifactFactory, dependencyResolver, conflictResolver)

    def setup() {
        config(root, 'root', 'default')
        _ * configuration.name >> 'root'
        _ * moduleDescriptorConverter.convert(_, _) >> root.descriptor
        _ * resolvedArtifactFactory.create(_, _, _) >> { owner, artifact, resolver ->
            return new DefaultResolvedArtifact(owner, artifact, null)
        }
    }

    def "does not resolve a given module selector more than once"() {
        given:
        def a = revision("a")
        def b = revision("b")
        def c = revision("c")
        traverses root, a
        traverses root, b
        traverses a, c
        doesNotResolve b, c

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        modules(result) == ids(a, b, c)
    }

    def "correctly notifies the resolved configuration listener"() {
        given:
        def a = revision("a")
        def b = revision("b")
        def c = revision("c")
        def d = revision("d")
        traverses root, a
        traverses root, b
        traverses a, c
        traversesMissing a, d

        when:
        builder.resolve(configuration, resolveData, listener)

        then:
        1 * listener.start(newId("group", "root", "1.0"))
        then:
        1 * listener.resolvedConfiguration({ it.name == 'root' }, { it*.requested.name == ['a', 'b'] })
        then:
        1 * listener.resolvedConfiguration({ it.name == 'a' }, { it*.requested.name == ['c', 'd'] && it*.failure.count { it != null } == 1 })
    }

    def "does not resolve a given dynamic module selector more than once"() {
        given:
        def a = revision("a")
        def b = revision("b")
        def c = revision("c")
        def d = revision("d")
        traverses root, a
        traverses root, b
        traverses root, c
        traverses a, d, revision: 'latest'
        doesNotResolve b, d, revision: 'latest'
        doesNotResolve c, d

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        modules(result) == ids(a, b, c, d)
    }

    def "does not include evicted module when selected module already traversed before conflict detected"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        def e = revision('e')
        traverses root, selected
        traverses selected, c
        traverses root, b
        traverses b, d
        doesNotResolve d, evicted // Conflict is deeper than all dependencies of selected module
        doesNotResolve evicted, e

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null, !null) >> { Collection<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            assert candidates*.revision == ['1.2', '1.1']
            return candidates.find { it.revision == '1.2' }
        }
        0 * conflictResolver._

        and:
        modules(result) == ids(selected, b, c, d)
    }

    def "does not include evicted module when evicted module already traversed before conflict detected"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        def e = revision('e')
        traverses root, evicted
        traverses evicted, c
        traverses root, b
        traverses b, d
        traverses d, selected // Conflict is deeper than all dependencies of other module
        traverses selected, e

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null, !null) >> { Collection<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            assert candidates*.revision == ['1.1', '1.2']
            return candidates.find { it.revision == '1.2' }
        }
        0 * conflictResolver._

        and:
        modules(result) == ids(selected, b, d, e)
    }

    def "does not include evicted module when path through evicted module is queued for traversal when conflict detected"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        def e = revision('e')
        traverses root, evicted
        traverses evicted, c
        doesNotResolve c, d
        traverses root, b
        traverses b, selected
        traverses selected, e

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null, !null) >> { Collection<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            assert candidates*.revision == ['1.1', '1.2']
            return candidates.find { it.revision == '1.2' }
        }
        0 * conflictResolver._

        and:
        modules(result) == ids(selected, b, e)
    }

    def "resolves when path through selected module is queued for traversal when conflict detected"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        def b = revision('b')
        def c = revision('c')
        traverses root, selected
        traverses selected, b
        doesNotResolve root, evicted
        doesNotResolve evicted, c

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null, !null) >> { Collection<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            assert candidates*.revision == ['1.2', '1.1']
            return candidates.find { it.revision == '1.2' }
        }
        0 * conflictResolver._

        and:
        modules(result) == ids(selected, b)
    }

    def "does not include evicted module when another path through evicted module traversed after conflict detected"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        traverses root, evicted
        doesNotResolve evicted, d
        traverses root, selected
        traverses selected, c
        traverses root, b
        doesNotResolve b, evicted

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null, !null) >> { Collection<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            assert candidates*.revision == ['1.1', '1.2']
            return candidates.find { it.revision == '1.2' }
        }
        0 * conflictResolver._

        and:
        modules(result) == ids(selected, b, c)
    }

    def "restarts conflict resolution when later conflict on same module discovered"() {
        given:
        def selectedA = revision('a', '1.2')
        def evictedA1 = revision('a', '1.1')
        def evictedA2 = revision('a', '1.0')
        def selectedB = revision('b', '2.2')
        def evictedB = revision('b', '2.1')
        def c = revision('c')
        traverses root, evictedA1
        traverses root, selectedA
        traverses selectedA, c
        traverses root, evictedB
        traverses root, selectedB
        doesNotResolve selectedB, evictedA2

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        1 * conflictResolver.select({ it*.revision == ['1.1', '1.2'] }, !null) >> { Collection<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            return candidates.find { it.revision == '1.2' }
        }
        1 * conflictResolver.select({ it*.revision == ['2.1', '2.2'] }, !null) >> { Collection<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            return candidates.find { it.revision == '2.2' }
        }
        1 * conflictResolver.select({ it*.revision == ['1.1', '1.2', '1.0'] }, !null) >> { Collection<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            return candidates.find { it.revision == '1.2' }
        }
        0 * conflictResolver._

        and:
        modules(result) == ids(selectedA, c, selectedB)
    }

    def "does not include module version that is excluded after conflict resolution has been applied"() {
        given:
        def a = revision('a')
        def b = revision('b')
        def evicted = revision('c', '1')
        def selected = revision('c', '2')
        def d = revision('d')
        def e = revision('e')
        traverses root, evicted
        traverses root, a, exclude: b
        doesNotResolve evicted, a
        traverses a, b
        traverses root, d
        traverses d, e
        traverses e, selected // conflict is deeper than 'b', to ensure 'b' has been visited

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        1 * conflictResolver.select({ it*.revision == ['1', '2'] }, !null) >> { Collection<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            return candidates.find { it.revision == '2' }
        }
        0 * conflictResolver._

        and:
        modules(result) == ids(a, selected, d, e)
    }

    def "does not include dependencies of module version that is no longer transitive after conflict resolution has been applied"() {
        given:
        def a = revision('a')
        def b = revision('b')
        def evicted = revision('c', '1')
        def selected = revision('c', '2')
        def d = revision('d')
        def e = revision('e')
        traverses root, evicted
        traverses root, a, transitive: false
        doesNotResolve evicted, a
        traverses a, b
        traverses root, d
        traverses d, e
        traverses e, selected // conflict is deeper than 'b', to ensure 'b' has been visited

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        1 * conflictResolver.select({ it*.revision == ['1', '2'] }, !null) >> { Collection<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            return candidates.find { it.revision == '2' }
        }
        0 * conflictResolver._

        and:
        modules(result) == ids(a, selected, d, e)
    }

    def "does not attempt to resolve a dependency whose target module is excluded earlier in the path"() {
        given:
        def a = revision('a')
        def b = revision('b')
        def c = revision('c')
        traverses root, a
        traverses a, b, exclude: c
        doesNotResolve b, c

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        modules(result) == ids(a, b)
    }

    def "does not include the artifacts of evicted modules"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        traverses root, selected
        doesNotResolve root, evicted

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null, !null) >> { Collection<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            return candidates.find { it.revision == '1.2' }
        }

        and:
        artifacts(result) == ids(selected)
    }

    def "does not include the artifacts of excluded modules when excluded by all paths"() {
        given:
        def a = revision('a')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        traverses root, a
        traverses a, b, exclude: c
        doesNotResolve b, c
        traverses root, d, exclude: c
        doesNotResolve d, c

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        modules(result) == ids(a, b, d)
        artifacts(result) == ids(a, b, d)
    }

    def "includes a module version when there is a path to the version that does not exclude it"() {
        given:
        def a = revision('a')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        traverses root, a
        traverses a, b, exclude: c
        doesNotResolve b, c
        traverses root, d
        traverses d, c

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        modules(result) == ids(a, b, c, d)
        artifacts(result) == ids(a, b, c, d)
    }

    def "ignores a new incoming path that includes a subset of those already included"() {
        given:
        def a = revision('a')
        def b = revision('b')
        def c = revision('c')
        traverses root, a
        traverses a, b
        traverses root, c, exclude: b
        doesNotResolve c, a

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        modules(result) == ids(a, b, c)
        artifacts(result) == ids(a, b, c)
    }

    def "ignores a new incoming path that includes the same set of module versions"() {
        given:
        def a = revision('a')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        def e = revision('e')
        traverses root, a, exclude: e
        traverses a, b
        traverses a, c
        traverses b, d
        doesNotResolve c, d
        doesNotResolve d, e

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        modules(result) == ids(a, b, c, d)
        artifacts(result) == ids(a, b, c, d)
    }

    def "restarts traversal when new incoming path excludes fewer module versions"() {
        given:
        def a = revision('a')
        def b = revision('b')
        def c = revision('c')
        traverses root, a, exclude: b
        traverses root, c
        doesNotResolve c, a
        traverses a, b

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        modules(result) == ids(a, b, c)
        artifacts(result) == ids(a, b, c)
    }

    def "does not traverse outgoing paths of a non-transitive dependency"() {
        given:
        def a = revision('a')
        def b = revision('b')
        def c = revision('c')
        traverses root, a
        traverses a, b, transitive: false
        doesNotResolve b, c

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        modules(result) == ids(a, b)
        artifacts(result) == ids(a, b)
    }

    def "reports shortest incoming paths for a failed dependency"() {
        given:
        def a = revision('a')
        def b = revision('b')
        def c = revision('c')
        traverses root, a
        traverses root, b
        doesNotResolve b, a
        traversesBroken a, c
        doesNotResolve b, c

        when:
        def result = builder.resolve(configuration, resolveData, listener)

        then:
        result.unresolvedModuleDependencies.size() == 1
        def unresolved = result.unresolvedModuleDependencies.iterator().next()
        unresolved.selector == new DefaultModuleVersionSelector('group', 'c', '1.0')
        unresolved.problem instanceof ModuleVersionResolveException

        when:
        result.rethrowFailure()

        then:
        ResolveException e = thrown()
        e.cause instanceof ModuleVersionResolveException
        e.cause.message.contains "group:root:1.0 > group:a:1.0"
        e.cause.message.contains "group:root:1.0 > group:b:1.0"
        !e.cause.message.contains("group:root:1.0 > group:b:1.0 > group:a:1.0")
    }

    def "reports failure to resolve version selector to module version"() {
        given:
        def a = revision('a')
        def b = revision('b')
        traverses root, a
        traverses root, b
        doesNotResolve b, a
        brokenSelector b, 'unknown'
        brokenSelector a, 'unknown'

        when:
        def result = builder.resolve(configuration, resolveData, listener)

        then:
        result.unresolvedModuleDependencies.size() == 1
        def unresolved = result.unresolvedModuleDependencies.iterator().next()
        unresolved.selector == new DefaultModuleVersionSelector('group', 'unknown', '1.0')
        unresolved.problem instanceof ModuleVersionResolveException

        when:
        result.rethrowFailure()

        then:
        ResolveException e = thrown()
        e.cause instanceof ModuleVersionResolveException
        e.cause.message.contains "group:root:1.0 > group:a:1.0"
        e.cause.message.contains "group:root:1.0 > group:b:1.0"
    }

    def "merges all failures for all dependencies with a given module version selector"() {
        given:
        def a = revision('a')
        def b = revision('b')
        def c = revision('c')
        traverses root, a
        traverses root, b
        traversesBroken a, c
        doesNotResolve b, c

        when:
        def result = builder.resolve(configuration, resolveData, listener)

        then:
        result.unresolvedModuleDependencies.size() == 1
        def unresolved = result.unresolvedModuleDependencies.iterator().next()
        unresolved.selector == new DefaultModuleVersionSelector('group', 'c', '1.0')
        unresolved.problem instanceof ModuleVersionResolveException

        when:
        result.rethrowFailure()

        then:
        ResolveException e = thrown()
        e.cause instanceof ModuleVersionResolveException
        e.cause.message.contains "group:root:1.0 > group:a:1.0"
        e.cause.message.contains "group:root:1.0 > group:b:1.0"
    }

    def "reports shortest incoming paths for a missing module version"() {
        given:
        def a = revision('a')
        def b = revision('b')
        def c = revision('c')
        traverses root, a
        traverses root, b
        doesNotResolve b, a
        traversesMissing a, c
        doesNotResolve b, c

        when:
        def result = builder.resolve(configuration, resolveData, listener)

        then:
        result.unresolvedModuleDependencies.size() == 1
        def unresolved = result.unresolvedModuleDependencies.iterator().next()
        unresolved.selector == new DefaultModuleVersionSelector('group', 'c', '1.0')
        unresolved.problem instanceof ModuleVersionNotFoundException

        when:
        result.rethrowFailure()

        then:
        ResolveException e = thrown()
        e.cause instanceof ModuleVersionNotFoundException
        e.cause.message.contains "group:root:1.0 > group:a:1.0"
        e.cause.message.contains "group:root:1.0 > group:b:1.0"
        !e.cause.message.contains("group:root:1.0 > group:b:1.0 > group:a:1.0")
    }

    def "merges all dependencies with a given module version selector when reporting missing version"() {
        given:
        def a = revision('a')
        def b = revision('b')
        def c = revision('c')
        traverses root, a
        traverses root, b
        traversesMissing a, c
        doesNotResolve b, c

        when:
        def result = builder.resolve(configuration, resolveData, listener)

        then:
        result.unresolvedModuleDependencies.size() == 1
        def unresolved = result.unresolvedModuleDependencies.iterator().next()
        unresolved.selector == new DefaultModuleVersionSelector('group', 'c', '1.0')
        unresolved.problem instanceof ModuleVersionResolveException

        when:
        result.rethrowFailure()

        then:
        ResolveException e = thrown()
        e.cause instanceof ModuleVersionNotFoundException
        e.cause.message.contains "group:root:1.0 > group:a:1.0"
        e.cause.message.contains "group:root:1.0 > group:b:1.0"
    }

    def "can handle a cycle in the incoming paths of a broken module"() {
        given:
        def a = revision('a')
        def b = revision('b')
        def c = revision('c')
        traverses root, a
        traverses a, b
        doesNotResolve b, a
        traversesMissing b, c

        when:
        def result = builder.resolve(configuration, resolveData, listener)

        then:
        result.unresolvedModuleDependencies.size() == 1
        def unresolved = result.unresolvedModuleDependencies.iterator().next()
        unresolved.selector == new DefaultModuleVersionSelector('group', 'c', '1.0')
        unresolved.problem instanceof ModuleVersionResolveException

        when:
        result.rethrowFailure()

        then:
        ResolveException e = thrown()
        e.cause instanceof ModuleVersionNotFoundException
        e.cause.message.contains "group:root:1.0 > group:a:1.0 > group:b:1.0"
    }

    def "does not report a path through an evicted version"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        def e = revision('e')
        traverses root, evicted
        traverses evicted, b
        traversesMissing b, c
        traverses root, d
        traverses d, e
        traverses e, selected
        doesNotResolve selected, c

        when:
        def result = builder.resolve(configuration, resolveData, listener)

        then:
        1 * conflictResolver.select(!null, !null) >> { Collection<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            return candidates.find { it.revision == '1.2' }
        }

        when:
        result.rethrowFailure()

        then:
        ResolveException ex = thrown()
        ex.cause instanceof ModuleVersionNotFoundException
        !ex.cause.message.contains("group:a:1.1")
        ex.cause.message.contains "group:root:1.0 > group:a:1.2"

        and:
        modules(result) == ids(selected, d, e)
    }

    def "fails when conflict resolution selects a version that does not exist"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        def b = revision('b')
        traverses root, evicted
        traverses root, b
        traversesMissing b, selected

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null, !null) >> { Collection<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            return candidates.find { it.revision == '1.2' }
        }

        and:
        ResolveException e = thrown()
        e.cause instanceof ModuleVersionNotFoundException
        e.cause.message.contains("group:root:1.0 > group:b:1.0")
    }

    def "does not fail when conflict resolution evicts a version that does not exist"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        def b = revision('b')
        traversesMissing root, evicted
        traverses root, b
        traverses b, selected

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null, !null) >> { Collection<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            return candidates.find { it.revision == '1.2' }
        }

        and:
        modules(result) == ids(selected, b)
    }

    def "does not fail when a broken version is evicted"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        def b = revision('b')
        def c = revision('c')
        traverses root, evicted
        traversesBroken evicted, b
        traverses root, c
        traverses c, selected

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null, !null) >> { Collection<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            return candidates.find { it.revision == '1.2' }
        }

        and:
        modules(result) == ids(selected, c)
    }

    def "direct dependency can force a particular version"() {
        given:
        def forced = revision("a", "1")
        def evicted = revision("a", "2")
        def b = revision("b")
        traverses root, b
        traverses root, forced, force: true
        doesNotResolve b, evicted

        when:
        def result = builder.resolve(configuration, resolveData, listener)
        result.rethrowFailure()

        then:
        modules(result) == ids(forced, b)
    }

    def revision(String name, String revision = '1.0') {
        DefaultModuleDescriptor descriptor = new DefaultModuleDescriptor(new ModuleRevisionId(new ModuleId("group", name), revision), "release", new Date())
        DefaultBuildableModuleVersionMetaData metaData = new DefaultBuildableModuleVersionMetaData()
        metaData.resolved(descriptor, false, null)
        config(metaData, 'default')
        descriptor.addArtifact('default', new DefaultArtifact(descriptor.moduleRevisionId, new Date(), "art1", "art", "zip"))
        return metaData
    }

    def config(ModuleVersionMetaData metaData, String name, String... extendsFrom) {
        def configuration = new Configuration(name, Configuration.Visibility.PUBLIC, null, extendsFrom, true, null)
        metaData.descriptor.addConfiguration(configuration)
        return configuration
    }

    def traverses(Map<String, ?> args = [:], ModuleVersionMetaData from, ModuleVersionMetaData to) {
        def descriptor = dependsOn(args, from.descriptor, to.descriptor.moduleRevisionId)
        def idResolveResult = selectorResolvesTo(descriptor, to.id);
        ModuleVersionResolveResult resolveResult = Mock()
        1 * idResolveResult.resolve() >> resolveResult
        1 * resolveResult.id >> to.id
        1 * resolveResult.metaData >> to
    }

    def doesNotResolve(Map<String, ?> args = [:], ModuleVersionMetaData from, ModuleVersionMetaData to) {
        def descriptor = dependsOn(args, from.descriptor, to.descriptor.moduleRevisionId)
        ModuleVersionIdResolveResult result = Mock()
        (0..1) * dependencyResolver.resolve({it.descriptor == descriptor}) >> result
        (0..1) * result.id >> to.id;
        _ * result.failure >> null
        _ * result.selectionReason >> null
        0 * result._
    }

    def traversesMissing(Map<String, ?> args = [:], ModuleVersionMetaData from, ModuleVersionMetaData to) {
        def descriptor = dependsOn(args, from.descriptor, to.descriptor.moduleRevisionId)
        def idResolveResult = selectorResolvesTo(descriptor, to.id)
        ModuleVersionResolveResult resolveResult = Mock()
        1 * idResolveResult.resolve() >> resolveResult
        1 * resolveResult.id >> { throw new ModuleVersionNotFoundException(newId("org", "a", "1.2")) }
    }

    def traversesBroken(Map<String, ?> args = [:], ModuleVersionMetaData from, ModuleVersionMetaData to) {
        def descriptor = dependsOn(args, from.descriptor, to.descriptor.moduleRevisionId)
        def idResolveResult = selectorResolvesTo(descriptor, to.id)
        ModuleVersionResolveResult resolveResult = Mock()
        1 * idResolveResult.resolve() >> resolveResult
        1 * resolveResult.id >> { throw new ModuleVersionResolveException(newSelector("a", "b", "c"), "broken") }
    }

    ModuleVersionIdentifier toModuleVersionIdentifier(ModuleRevisionId moduleRevisionId) {
        ModuleVersionIdentifier moduleVersionIdentifier = Mock();
        (0..2) * moduleVersionIdentifier.group >> moduleRevisionId.organisation;
        (0..2) * moduleVersionIdentifier.name >> moduleRevisionId.name;
        (0..2) * moduleVersionIdentifier.version >> moduleRevisionId.revision;
        moduleVersionIdentifier
    }

    def brokenSelector(Map<String, ?> args = [:], ModuleVersionMetaData from, String to) {
        def descriptor = dependsOn(args, from.descriptor, ModuleRevisionId.newInstance("group", to, "1.0"))
        ModuleVersionIdResolveResult result = Mock()
        (0..1) * dependencyResolver.resolve({it.descriptor == descriptor}) >> result
        _ * result.failure >> new ModuleVersionResolveException(newSelector("a", "b", "c"), "broken")
        _ * result.selectionReason >> VersionSelectionReasons.REQUESTED
        0 * result._
    }

    def dependsOn(Map<String, ?> args = [:], DefaultModuleDescriptor from, ModuleRevisionId to) {
        ModuleDependency moduleDependency = Mock()
        def dependencyId = args.revision ? new ModuleRevisionId(to.moduleId, args.revision) : to
        boolean transitive = args.transitive == null || args.transitive
        boolean force = args.force
        def descriptor = new EnhancedDependencyDescriptor(moduleDependency, from, dependencyId, force, false, transitive)
        descriptor.addDependencyConfiguration("default", "default")
        if (args.exclude) {
            descriptor.addExcludeRule("default", new DefaultExcludeRule(new ArtifactId(
                    args.exclude.descriptor.moduleRevisionId.moduleId, PatternMatcher.ANY_EXPRESSION,
                    PatternMatcher.ANY_EXPRESSION,
                    PatternMatcher.ANY_EXPRESSION),
                    ExactPatternMatcher.INSTANCE, null))
        }
        from.addDependency(descriptor)
        return descriptor
    }

    def selectorResolvesTo(DependencyDescriptor descriptor, ModuleVersionIdentifier to) {
        ModuleVersionIdResolveResult result = Mock()
        1 * dependencyResolver.resolve({it.descriptor == descriptor}) >> result
        1 * result.id >> to
        return result
    }

    def ids(ModuleVersionMetaData... descriptors) {
        return descriptors.collect { it.id } as Set
    }

    def modules(LenientConfiguration config) {
        Set<ModuleVersionIdentifier> result = new LinkedHashSet<ModuleVersionIdentifier>()
        List<ResolvedDependency> queue = []
        queue.addAll(config.getFirstLevelModuleDependencies({ true } as Spec))
        while (!queue.empty) {
            def node = queue.remove(0)
            result.add(node.module.id)
            queue.addAll(0, node.children)
        }
        return result
    }

    def artifacts(LenientConfiguration config) {
        return config.resolvedArtifacts.collect { it.moduleVersion.id } as Set
    }
}
