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

import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.Action
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.artifacts.ComponentSelectorConverter
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ResolveContext
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.dsl.ModuleReplacementsData
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencySubstitutionApplicator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphPathResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.DependencyGraphBuilder
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultCapabilitiesConflictHandler
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultConflictHandler
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.specs.Specs
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata
import org.gradle.internal.component.local.model.DefaultLocalConfigurationMetadata
import org.gradle.internal.component.local.model.DslOriginDependencyMetadataWrapper
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import org.gradle.internal.component.model.ComponentIdGenerator
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.LocalComponentDependencyMetadata
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.resolve.ModuleVersionNotFoundException
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult
import org.gradle.internal.resolve.result.BuildableComponentResolveResult
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector
import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newProjectId

class DependencyGraphBuilderTest extends Specification {
    def resolveContext = Mock(ResolveContext) {
        getResolutionStrategy() >> Stub(ResolutionStrategyInternal)
    }
    def conflictResolver = Mock(ModuleConflictResolver)
    def idResolver = Mock(DependencyToComponentIdResolver)
    def metaDataResolver = Mock(ComponentMetaDataResolver)
    def attributesSchema = Mock(AttributesSchemaInternal)
    def attributes = ImmutableAttributes.EMPTY
    def root = rootProject()
    def moduleReplacements = Mock(ModuleReplacementsData)
    def moduleIdentifierFactory = Mock(ImmutableModuleIdentifierFactory) {
        module(_, _) >> { args ->
            DefaultModuleIdentifier.newId(*args)
        }
    }
    def moduleExclusions = new ModuleExclusions()
    def buildOperationProcessor = Mock(BuildOperationExecutor) {
        def queue = Mock(BuildOperationQueue) {
            add(_) >> { args ->
                args[0].run()
            }
        }
        runAll(_) >> { args ->
            args[0].execute(queue)
        }
    }
    def dependencySubstitutionApplicator = new DefaultDependencySubstitutionApplicator(DependencyManagementTestUtil.componentSelectionDescriptorFactory(), Mock(Action), TestUtil.instantiatorFactory().decorateScheme().instantiator())
    def componentSelectorConverter = Mock(ComponentSelectorConverter) {
        getModule(_) >> { ComponentSelector selector ->
            DefaultModuleIdentifier.newId(selector.group, selector.module)
        }
    }

    def moduleConflictHandler = new DefaultConflictHandler(conflictResolver, moduleReplacements)
    def capabilitiesConflictHandler = new DefaultCapabilitiesConflictHandler()
    def versionComparator = new DefaultVersionComparator()
    def versionSelectorScheme = new DefaultVersionSelectorScheme(versionComparator, new VersionParser())
    def desugaring = new AttributeDesugaring(AttributeTestUtil.attributesFactory())
    def resolveStateFactory = new LocalComponentGraphResolveStateFactory(desugaring, new ComponentIdGenerator())

    DependencyGraphBuilder builder

    def setup() {
        _ * resolveContext.name >> 'root'
        _ * resolveContext.toRootComponentMetaData() >> root

        builder = new DependencyGraphBuilder(idResolver, metaDataResolver, moduleConflictHandler, capabilitiesConflictHandler, Specs.satisfyAll(), attributesSchema, moduleExclusions, buildOperationProcessor, dependencySubstitutionApplicator, componentSelectorConverter, AttributeTestUtil.attributesFactory(), desugaring, versionSelectorScheme, versionComparator.asVersionComparator(), resolveStateFactory, new ComponentIdGenerator(), new VersionParser())
    }

    private TestGraphVisitor resolve(DependencyGraphBuilder builder = this.builder) {
        def graphVisitor = new TestGraphVisitor()
        builder.resolve(resolveContext, graphVisitor, false)
        return graphVisitor
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
        def result = resolve()
        result.rethrowFailure()

        then:
        result.components == ids(root, a, b, c)
    }

    def "honors component replacements"() {
        given:
        def a = revision('a') // a->c
        def b = revision('b') // b->d, replaces a
        def c = revision('c') //transitive of evicted a
        def d = revision('d')

        doesNotTraverse root, a
        traverses root, b
        doesNotResolve a, c
        traverses b, d

        def moduleA = DefaultModuleIdentifier.newId("group", "a")
        def moduleB = DefaultModuleIdentifier.newId("group", "b")
        moduleReplacements.participatesInReplacements(moduleA) >> true
        moduleReplacements.participatesInReplacements(moduleB) >> true
        moduleReplacements.getReplacementFor(moduleA) >> new ModuleReplacementsData.Replacement(moduleB, null)
        1 * conflictResolver.select(!null) >> {  args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            def sel = candidates.find { it.id.name == 'b' }
            assert sel
            details.select(sel)
        }
        0 * conflictResolver._

        when:
        def result = resolve()
        result.rethrowFailure()

        then:
        result.components == ids(root, b, d)
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
        traverses a, d, revision: 'latest.release'
        doesNotResolve b, d, revision: 'latest.release'
        doesNotResolve c, d

        when:
        def result = resolve()
        result.rethrowFailure()

        then:
        result.components == ids(root, a, b, c, d)
    }

    def "does not include evicted module or dependencies when selected module already traversed before conflict detected"() {
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
        doesNotTraverse d, evicted // Conflict is deeper than all dependencies of selected module
        doesNotResolve evicted, e

        when:
        def result = resolve()
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null) >> { args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            assert candidates*.version as Set == ['1.2', '1.1'] as Set
            details.select(candidates.find { it.version == '1.2' })
        }
        0 * conflictResolver._

        and:
        result.components == ids(root, selected, b, c, d)
    }

    def "does not include evicted module or dependencies when evicted module already traversed before conflict detected"() {
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
        def result = resolve()
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null) >> { args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            assert candidates*.version == ['1.2', '1.1']
            details.select(candidates.find { it.version == '1.2' })
        }
        0 * conflictResolver._

        and:
        result.components == ids(root, selected, b, d, e)
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
        def result = resolve()
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null) >> { args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            assert candidates*.version == ['1.2', '1.1']
            details.select(candidates.find { it.version == '1.2' })
        }
        0 * conflictResolver._

        and:
        result.components == ids(root, selected, b, e)
    }

    def "includes dependencies of evicted module another path to dependency"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        traverses root, evicted
        traverses evicted, c
        traverses evicted, d
        traverses root, b
        traverses b, selected
        doesNotResolve selected, c

        when:
        def result = resolve()
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null) >> { args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            assert candidates*.version as Set == ['1.2', '1.1'] as Set
            details.select(candidates.find { it.version == '1.2' })
        }
        0 * conflictResolver._

        and:
        result.components == ids(root, selected, b, c)
    }

    def "does not include evicted module with multiple incoming paths"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        traverses root, evicted
        traverses root, b
        doesNotResolve b, evicted
        traverses root, c
        traverses c, selected
        traverses selected, d

        when:
        def result = resolve()
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null) >> { args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            assert candidates*.version == ['1.2', '1.1']
            details.select(candidates.find { it.version == '1.2' })
        }
        0 * conflictResolver._

        and:
        result.components == ids(root, selected, b, c, d)
    }

    def"does not include evicted module required by another evicted module"() {
        given:
        def selectedA = revision('a', '1.2')
        def evictedA = revision('a', '1.1')
        def selectedB = revision('b', '2.2')
        def evictedB = revision('b', '2.1')
        def c = revision('c')
        def d = revision('d')
        traverses root, evictedA
        traverses evictedA, evictedB
        traverses root, c
        doesNotResolve c, evictedB
        traverses root, d
        traverses d, selectedA
        traverses selectedA, selectedB

        when:
        def result = resolve()
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null) >> { args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            assert candidates*.version == ['1.2', '1.1']
            details.select(candidates.find { it.version == '1.2' })
        }
        1 * conflictResolver.select(!null) >> { args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            assert candidates*.version == ['2.2', '2.1']
            details.select(candidates.find { it.version == '2.2' })
        }
        0 * conflictResolver._

        and:
        result.components == ids(root, selectedA, selectedB, c, d)
    }

    def "resolves when path through selected module is queued for traversal when conflict detected"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        def b = revision('b')
        def c = revision('c')
        traverses root, selected
        traverses selected, b
        doesNotTraverse root, evicted
        doesNotResolve evicted, c

        when:
        def result = resolve()
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null) >> { args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            assert candidates*.version as Set == ['1.2', '1.1'] as Set
            details.select(candidates.find { it.version == '1.2' })
        }
        0 * conflictResolver._

        and:
        result.components == ids(root, selected, b)
    }

    def "does not include evicted module when another path through evicted module traversed after conflict detected"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        doesNotTraverse root, evicted
        doesNotResolve evicted, d
        traverses root, selected
        traverses selected, c
        traverses root, b
        doesNotResolve b, evicted

        when:
        def result = resolve()
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null) >> { args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            assert candidates*.version == ['1.2', '1.1']
            details.select(candidates.find { it.version == '1.2' })
        }
        0 * conflictResolver._

        and:
        result.components == ids(root, selected, b, c)
    }

    def "restarts conflict resolution when later conflict on same module discovered"() {
        given:
        def selectedA = revision('a', '1.2')
        def evictedA1 = revision('a', '1.1')
        def evictedA2 = revision('a', '1.0')
        def selectedB = revision('b', '2.2')
        def evictedB = revision('b', '2.1')
        def c = revision('c')
        doesNotTraverse root, evictedA1
        traverses root, selectedA
        traverses selectedA, c
        doesNotTraverse root, evictedB
        traverses root, selectedB
        doesNotTraverse selectedB, evictedA2

        when:
        def result = resolve()
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(_) >> { args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            assert candidates*.version == ['1.2', '1.1']
            details.select(candidates.find { it.version == '1.2' })
        }
        1 * conflictResolver.select(_) >> { args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            assert candidates*.version == ['2.2', '2.1']
            details.select(candidates.find { it.version == '2.2' })
        }
        1 * conflictResolver.select(_) >> { args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            assert candidates*.version == ['1.2', '1.1', '1.0']
            details.select(candidates.find { it.version == '1.2' })
        }
        0 * conflictResolver._

        and:
        result.components == ids(root, selectedA, c, selectedB)
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
        def result = resolve()
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(_) >> { args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            assert candidates*.version == ['2', '1']
            details.select(candidates.find { it.version == '2' })
        }
        0 * conflictResolver._

        and:
        result.components == ids(root, a, selected, d, e)
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
        def result = resolve()
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(_) >> { args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            assert candidates*.version == ['2', '1']
            details.select(candidates.find { it.version == '2' })
        }
        0 * conflictResolver._

        and:
        result.components == ids(root, a, selected, d, e)
    }

    def "does not include filtered dependencies"() {
        given:
        def spec = { DependencyMetadata dep -> dep.selector.module != 'c' }
        builder = new DependencyGraphBuilder(idResolver, metaDataResolver, moduleConflictHandler, capabilitiesConflictHandler, spec, attributesSchema, moduleExclusions, buildOperationProcessor, dependencySubstitutionApplicator, componentSelectorConverter, AttributeTestUtil.attributesFactory(), desugaring, versionSelectorScheme, Stub(Comparator), resolveStateFactory, new ComponentIdGenerator(), new VersionParser())

        def a = revision('a')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        traverses root, a
        traverses a, b
        doesNotResolve b, c
        traverses root, d
        doesNotResolve d, c

        when:
        def result = resolve(builder)
        result.rethrowFailure()

        then:
        result.components == ids(root, a, b, d)
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
        def result = resolve()
        result.rethrowFailure()

        then:
        result.components == ids(root, a, b)
    }

    def "does not include excluded modules when excluded by all paths"() {
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
        def result = resolve()
        result.rethrowFailure()

        then:
        result.components == ids(root, a, b, d)
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
        def result = resolve()
        result.rethrowFailure()

        then:
        result.components == ids(root, a, b, c, d)
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
        def result = resolve()
        result.rethrowFailure()

        then:
        result.components == ids(root, a, b, c)
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
        def result = resolve()
        result.rethrowFailure()

        then:
        result.components == ids(root, a, b, c, d)
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
        def result = resolve()
        result.rethrowFailure()

        then:
        result.components == ids(root, a, b, c)
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
        def result = resolve()
        result.rethrowFailure()

        then:
        result.components == ids(root, a, b)
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
        def result = resolve()

        then:
        result.unresolvedDependencies == [newSelector(DefaultModuleIdentifier.newId('group', 'c'), new DefaultMutableVersionConstraint('1.0'))] as Set

        when:
        result.rethrowFailure()

        then:
        ResolveException e = thrown()
        e.cause instanceof ModuleVersionResolveException
        e.cause.message.contains "project :root > group:a:1.0"
        e.cause.message.contains "project :root > group:b:1.0"
        !e.cause.message.contains("project :root > group:b:1.0 > group:a:1.0")
    }

    def "reports failure to resolve version selector to module version"() {
        given:
        def a = revision('a')
        def b = revision('b')
        traverses root, a
        traverses root, b
        doesNotResolve b, a
        brokenSelector a, 'unknown'
        doesNotResolve b, revision('unknown')

        when:
        def result = resolve()

        then:
        result.unresolvedDependencies == [newSelector(DefaultModuleIdentifier.newId('group', 'unknown'), new DefaultMutableVersionConstraint('1.0'))] as Set

        when:
        result.rethrowFailure()

        then:
        ResolveException e = thrown()
        e.cause instanceof ModuleVersionResolveException
        e.cause.message.contains "project :root > group:a:1.0"
        e.cause.message.contains "project :root > group:b:1.0"
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
        def result = resolve()

        then:
        result.unresolvedDependencies == [newSelector(DefaultModuleIdentifier.newId('group', 'c'), new DefaultMutableVersionConstraint('1.0'))] as Set

        when:
        result.rethrowFailure()

        then:
        ResolveException e = thrown()
        e.cause instanceof ModuleVersionResolveException
        e.cause.message.contains "project :root > group:a:1.0"
        e.cause.message.contains "project :root > group:b:1.0"
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
        def result = resolve()

        then:
        result.unresolvedDependencies == [newSelector(DefaultModuleIdentifier.newId('group', 'c'), new DefaultMutableVersionConstraint('1.0'))] as Set

        when:
        result.rethrowFailure()

        then:
        ResolveException e = thrown()
        e.cause instanceof ModuleVersionNotFoundException
        e.cause.message.contains "project :root > group:a:1.0"
        e.cause.message.contains "project :root > group:b:1.0"
        !e.cause.message.contains("project :root > group:b:1.0 > group:a:1.0")
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
        def result = resolve()

        then:
        result.unresolvedDependencies == [newSelector(DefaultModuleIdentifier.newId('group', 'c'), new DefaultMutableVersionConstraint('1.0'))] as Set

        when:
        result.rethrowFailure()

        then:
        ResolveException e = thrown()
        e.cause instanceof ModuleVersionNotFoundException
        e.cause.message.contains "project :root > group:a:1.0"
        e.cause.message.contains "project :root > group:b:1.0"
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
        def result = resolve()

        then:
        result.unresolvedDependencies == [newSelector(DefaultModuleIdentifier.newId('group', 'c'), new DefaultMutableVersionConstraint('1.0'))] as Set

        when:
        result.rethrowFailure()

        then:
        ResolveException e = thrown()
        e.cause instanceof ModuleVersionNotFoundException
        e.cause.message.contains "project :root > group:a:1.0 > group:b:1.0"
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
        def result = resolve()

        then:
        1 * conflictResolver.select(!null) >> { args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            details.select(candidates.find { it.version == '1.2' })
        }

        when:
        result.rethrowFailure()

        then:
        ResolveException ex = thrown()
        ex.cause instanceof ModuleVersionNotFoundException
        !ex.cause.message.contains("group:a:1.1")
        ex.cause.message.contains "project :root > group:a:1.2"

        and:
        result.components == ids(root, selected, d, e)
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
        def result = resolve()
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null) >> { args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            details.select(candidates.find { it.version == '1.2' })
        }

        and:
        ResolveException e = thrown()
        e.cause instanceof ModuleVersionNotFoundException
        e.cause.message.contains("project :root")
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
        def result = resolve()
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null) >> { args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            details.select(candidates.find { it.version == '1.2' })
        }

        and:
        result.components == ids(root, selected, b)
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
        def result = resolve()
        result.rethrowFailure()

        then:
        1 * conflictResolver.select(!null) >> { args ->
            def details = args[0]
            Collection<ComponentResolutionState> candidates = details.candidates
            details.select(candidates.find { it.version == '1.2' })
        }

        and:
        result.components == ids(root, selected, c)
    }

    def "direct dependency can force a particular version"() {
        given:
        def forced = revision("a", "1")
        def evicted = revision("a", "2")
        def b = revision("b")
        traverses root, b
        traverses root, forced, force: true
        doesNotTraverse b, evicted

        when:
        def result = resolve()
        result.rethrowFailure()

        then:
        result.components == ids(root, forced, b)
    }

    def revision(String name, String revision = '1.0') {
        // TODO Shouldn't really be using the local component implementation here
        def id = newId("group", name, revision)
        def componentId = DefaultModuleComponentIdentifier.newId(id)

        def artifacts = [new PublishArtifactLocalArtifactMetadata(componentId, new DefaultPublishArtifact("art1", "zip", "art", null, new Date(), new File("art1.zip")))]
        def defaultConfiguration = new DefaultLocalConfigurationMetadata(
            "default", "defaultConfig", componentId, true, true, ["default"] as Set, attributes, ImmutableCapabilities.EMPTY,
            true, false, true, [], [] as Set, [],
            [] as Set, TestUtil.calculatedValueContainerFactory(), artifacts
        )

        def configurations = new DefaultLocalComponentMetadata.ConfigurationsMapMetadataFactory(["default": defaultConfiguration])
        return new DefaultLocalComponentMetadata(id, componentId, "release", attributesSchema, configurations, null)
    }

    def rootProject() {
        // TODO Shouldn't really be using the local component implementation here
        def componentId = newProjectId(":root")

        def artifacts = [new PublishArtifactLocalArtifactMetadata(componentId, new DefaultPublishArtifact("art1", "zip", "art", null, new Date(), new File("art1.zip")))]
        def defaultConfiguration = new DefaultLocalConfigurationMetadata(
            "default", "defaultConfig", componentId, true, true, ["default"] as Set, attributes, ImmutableCapabilities.EMPTY,
            true, false, true, [], [] as Set, [],
            [] as Set, TestUtil.calculatedValueContainerFactory(), artifacts
        )

        def rootConfiguration = new DefaultLocalConfigurationMetadata(
            "root", "rootConfig", componentId, true, true, ["default", "root"] as Set, attributes, ImmutableCapabilities.EMPTY,
            true, false, true, defaultConfiguration.getDependencies(), [] as Set, [],
            [] as Set, TestUtil.calculatedValueContainerFactory(), []
        )

        def configurations = new DefaultLocalComponentMetadata.ConfigurationsMapMetadataFactory(["default": defaultConfiguration, "root": rootConfiguration])
        return new DefaultLocalComponentMetadata(newId("group", "root", "1.0"), componentId, "release", attributesSchema, configurations, null)
    }

    def traverses(Map<String, ?> args = [:], def from, ComponentResolveMetadata to) {
        def dependencyMetaData = dependsOn(args, from, to.moduleVersionId)
        selectorResolvesTo(dependencyMetaData, to.id, to.moduleVersionId)
        println "Traverse $from to ${to.id}"
        1 * metaDataResolver.resolve(to.id, _, _) >> { ComponentIdentifier id, ComponentOverrideMetadata requestMetaData, BuildableComponentResolveResult result ->
            println "Called ${to.id}"
            result.resolved(resolveStateFactory.stateFor(to), Stub(ComponentGraphSpecificResolveState))
        }
    }

    def doesNotTraverse(Map<String, ?> args = [:], def from, ComponentResolveMetadata to) {
        def dependencyMetaData = dependsOn(args, from, to.moduleVersionId)
        selectorResolvesTo(dependencyMetaData, to.id, to.moduleVersionId)
        0 * metaDataResolver.resolve(to.id, _, _)
    }

    def doesNotResolve(Map<String, ?> args = [:], def from, ComponentResolveMetadata to) {
        def dependencyMetaData = dependsOn(args, from, to.moduleVersionId)
        0 * idResolver.resolve(dependencyMetaData, _, _, _)
        0 * metaDataResolver.resolve(to.id, _, _)
    }

    def traversesMissing(Map<String, ?> args = [:], def from, ComponentResolveMetadata to) {
        def dependencyMetaData = dependsOn(args, from, to.moduleVersionId)
        selectorResolvesTo(dependencyMetaData, to.id, to.moduleVersionId)
        1 * metaDataResolver.resolve(to.id, _, _) >> { ComponentIdentifier id, ComponentOverrideMetadata requestMetaData, BuildableComponentResolveResult result ->
            result.notFound(to.id)
        }
    }

    def traversesBroken(Map<String, ?> args = [:], def from, ComponentResolveMetadata to) {
        def dependencyMetaData = dependsOn(args, from, to.moduleVersionId)
        selectorResolvesTo(dependencyMetaData, to.id, to.moduleVersionId)
        1 * metaDataResolver.resolve(to.id, _, _) >> { ComponentIdentifier id, ComponentOverrideMetadata requestMetaData, BuildableComponentResolveResult result ->
            org.gradle.internal.Factory<String> broken = { "broken" }
            result.failed(new ModuleVersionResolveException(newSelector(DefaultModuleIdentifier.newId("a", "b"), new DefaultMutableVersionConstraint("c")), broken))
        }
    }

    ModuleVersionIdentifier toModuleVersionIdentifier(ModuleRevisionId moduleRevisionId) {
        ModuleVersionIdentifier moduleVersionIdentifier = Mock();
        (0..2) * moduleVersionIdentifier.group >> moduleRevisionId.organisation;
        (0..2) * moduleVersionIdentifier.name >> moduleRevisionId.name;
        (0..2) * moduleVersionIdentifier.version >> moduleRevisionId.revision;
        moduleVersionIdentifier
    }

    def brokenSelector(Map<String, ?> args = [:], def from, String to) {
        def dependencyMetaData = dependsOn(args, from, newId("group", to, "1.0"))
        1 * idResolver.resolve(dependencyMetaData, _, _, _) >> { DependencyMetadata dep, VersionSelector acceptor, VersionSelector rejector, BuildableComponentIdResolveResult result ->
            org.gradle.internal.Factory<String> broken = { "broken" }
            result.failed(new ModuleVersionResolveException(newSelector(DefaultModuleIdentifier.newId("a", "b"), new DefaultMutableVersionConstraint("c")), broken))
        }
    }

    def dependsOn(Map<String, ?> args = [:], ComponentResolveMetadata from, ModuleVersionIdentifier to) {
        ModuleVersionIdentifier dependencyId = args.revision ? newId(DefaultModuleIdentifier.newId(to.group, to.name), args.revision) : to
        boolean transitive = args.transitive == null || args.transitive
        boolean force = args.force
        boolean optional = args.optional ?: false
        ComponentSelector componentSelector = newSelector(DefaultModuleIdentifier.newId(dependencyId.group, dependencyId.name), new DefaultMutableVersionConstraint(dependencyId.version))
        List<ExcludeMetadata> excludeRules = []
        if (args.exclude) {
            ComponentResolveMetadata excluded = args.exclude
            excludeRules << new DefaultExclude(moduleIdentifierFactory.module(excluded.moduleVersionId.group, excluded.moduleVersionId.name))
        }
        def dependencyMetaData = new LocalComponentDependencyMetadata(from.id, componentSelector,
                "default", null, ImmutableAttributes.EMPTY, "default", [] as List<IvyArtifactName>,
                excludeRules, force, false, transitive, false, false, null)
        dependencyMetaData = new DslOriginDependencyMetadataWrapper(dependencyMetaData, Stub(ModuleDependency) {
            getAttributes() >> ImmutableAttributes.EMPTY
        })
        from.getConfiguration("default").getDependencies().add(dependencyMetaData)
        return dependencyMetaData
    }

    def selectorResolvesTo(DependencyMetadata dependencyMetaData, ComponentIdentifier id, ModuleVersionIdentifier mvId) {
        1 * idResolver.resolve(dependencyMetaData, _, _, _) >> { DependencyMetadata dep, VersionSelector acceptor, VersionSelector rejector, BuildableComponentIdResolveResult result ->
            result.resolved(id, mvId)
        }
    }

    def ids(ComponentResolveMetadata... descriptors) {
        return descriptors.collect { it.moduleVersionId } as Set
    }

    static class TestGraphVisitor implements DependencyGraphVisitor {
        def root
        def components = new LinkedHashSet()
        final Map<ComponentSelector, FailureDetails> failures = new LinkedHashMap<>()

        Set<ComponentSelector> getUnresolvedDependencies() {
            return failures.keySet()
        }

        @Override
        void start(RootGraphNode root) {
            this.root = root
        }

        @Override
        void visitNode(DependencyGraphNode node) {
            components.add(node.owner.moduleVersion)
        }

        @Override
        void visitSelector(DependencyGraphSelector selector) {
        }

        @Override
        void visitEdges(DependencyGraphNode node) {
            node.outgoingEdges.each {
                if (it.failure) {
                    def breakage = failures.get(it.requested)
                    if (breakage == null) {
                        breakage = new FailureDetails(it.failure)
                        failures.put(it.requested, breakage)
                    }
                    breakage.requiredBy << it.from
                }
            }
        }

        void rethrowFailure() {
            if (failures.isEmpty()) {
                return
            }

            throw new ResolveException("config", failures.values().collect {
                it.failure.withIncomingPaths(DependencyGraphPathResolver.calculatePaths(it.requiredBy, root))
            })
        }

        @Override
        void finish(DependencyGraphNode root) {
        }

        static class FailureDetails {
            final List<DependencyGraphNode> requiredBy = []
            final ModuleVersionResolveException failure

            FailureDetails(ModuleVersionResolveException failure) {
                this.failure = failure
            }
        }
    }
}
