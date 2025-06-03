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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.Action
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ComponentSelectorConverter
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.configurations.ConflictResolution
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.dsl.ImmutableModuleReplacements
import org.gradle.api.internal.artifacts.ivyservice.ResolutionParameters
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencySubstitutionApplicator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultExcludeRuleConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultLocalVariantGraphResolveStateBuilder
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyMetadataFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphPathResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.DependencyGraphBuilder
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.initialization.StandaloneDomainObjectContext
import org.gradle.api.specs.Spec
import org.gradle.internal.Describables
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.local.model.DefaultLocalVariantGraphResolveMetadata
import org.gradle.internal.component.local.model.DefaultLocalVariantGraphResolveState
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata
import org.gradle.internal.component.local.model.LocalComponentGraphResolveMetadata
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState
import org.gradle.internal.component.local.model.LocalVariantMetadata
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import org.gradle.internal.component.model.ComponentConfigurationIdentifier
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState
import org.gradle.internal.component.model.ComponentIdGenerator
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.GraphVariantSelector
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.LocalComponentDependencyMetadata
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.model.CalculatedValue
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
    def conflictResolver = Mock(ModuleConflictResolver)
    def idResolver = Mock(DependencyToComponentIdResolver)
    def metaDataResolver = Mock(ComponentMetaDataResolver)
    def attributesSchema = ImmutableAttributesSchema.EMPTY
    def attributes = ImmutableAttributes.EMPTY
    def moduleReplacements = Mock(ImmutableModuleReplacements)
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
    def dependencySubstitutionApplicator = new DefaultDependencySubstitutionApplicator(DependencyManagementTestUtil.componentSelectionDescriptorFactory(), Mock(Action), TestUtil.instantiatorFactory())
    def componentSelectorConverter = Mock(ComponentSelectorConverter) {
        getModule(_) >> { ComponentSelector selector ->
            DefaultModuleIdentifier.newId(selector.group, selector.module)
        }
    }

    def versionComparator = new DefaultVersionComparator()
    def versionSelectorScheme = new DefaultVersionSelectorScheme(versionComparator, new VersionParser())
    def desugaring = new AttributeDesugaring(AttributeTestUtil.attributesFactory())
    def resolveStateFactory = new LocalComponentGraphResolveStateFactory(
        desugaring,
        new ComponentIdGenerator(),
        new DefaultLocalVariantGraphResolveStateBuilder(
            new ComponentIdGenerator(),
            Mock(DependencyMetadataFactory),
            new DefaultExcludeRuleConverter(new DefaultImmutableModuleIdentifierFactory())
        ),
        TestUtil.calculatedValueContainerFactory()
    )

    def variantSelector = new GraphVariantSelector(AttributeTestUtil.services(), DependencyManagementTestUtil.newFailureHandler())

    DependencyGraphBuilder builder = new DependencyGraphBuilder(
        moduleExclusions,
        AttributeTestUtil.attributesFactory(),
        AttributeTestUtil.services(),
        desugaring,
        versionSelectorScheme,
        versionComparator,
        new ComponentIdGenerator(),
        new VersionParser(),
        variantSelector,
        buildOperationProcessor
    )

    def root = rootProject()

    private TestGraphVisitor resolve(Spec<? super DependencyMetadata> edgeFilter = { true }) {
        def graphVisitor = new TestGraphVisitor()

        ResolutionParameters.FailureResolutions failureResolutions = () -> []

        builder.resolve(
            root.component,
            root.variant,
            [],
            edgeFilter,
            componentSelectorConverter,
            idResolver,
            metaDataResolver,
            moduleReplacements,
            dependencySubstitutionApplicator,
            conflictResolver,
            [],
            ConflictResolution.latest,
            false,
            false,
            failureResolutions,
            graphVisitor
        )

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
        moduleReplacements.getReplacementFor(moduleA) >> new ImmutableModuleReplacements.Replacement(moduleB, null)
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
        def result = resolve(spec)
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
        DefaultMultiCauseException e = thrown()
        e.cause instanceof ModuleVersionResolveException
        e.cause.message.contains "unknown > group:a:1.0"
        e.cause.message.contains "unknown > group:b:1.0"
        !e.cause.message.contains("unknown > group:b:1.0 > group:a:1.0")
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
        DefaultMultiCauseException e = thrown()
        e.cause instanceof ModuleVersionResolveException
        e.cause.message.contains "unknown > group:a:1.0"
        e.cause.message.contains "unknown > group:b:1.0"
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
        DefaultMultiCauseException e = thrown()
        e.cause instanceof ModuleVersionResolveException
        e.cause.message.contains "unknown > group:a:1.0"
        e.cause.message.contains "unknown > group:b:1.0"
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
        DefaultMultiCauseException e = thrown()
        e.cause instanceof ModuleVersionNotFoundException
        e.cause.message.contains "unknown > group:a:1.0"
        e.cause.message.contains "unknown > group:b:1.0"
        !e.cause.message.contains("unknown > group:b:1.0 > group:a:1.0")
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
        DefaultMultiCauseException e = thrown()
        e.cause instanceof ModuleVersionNotFoundException
        e.cause.message.contains "unknown > group:a:1.0"
        e.cause.message.contains "unknown > group:b:1.0"
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
        DefaultMultiCauseException e = thrown()
        e.cause instanceof ModuleVersionNotFoundException
        e.cause.message.contains "unknown > group:a:1.0 > group:b:1.0"
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
        DefaultMultiCauseException ex = thrown()
        ex.cause instanceof ModuleVersionNotFoundException
        !ex.cause.message.contains("group:a:1.1")
        ex.cause.message.contains "unknown > group:a:1.2"

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
        DefaultMultiCauseException e = thrown()
        e.cause instanceof ModuleVersionNotFoundException
        e.cause.message.contains("unknown")
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

    TestComponent revision(String name, String revision = '1.0') {
        // TODO Shouldn't really be using the local component implementation here
        def id = newId("group", name, revision)
        def componentId = DefaultModuleComponentIdentifier.newId(id)

        def artifacts = [new PublishArtifactLocalArtifactMetadata(componentId, new DefaultPublishArtifact("art1", "zip", "art", null, new Date(), new File("art1.zip")))]
        def defaultVariant = createVariantMetadata("default", componentId, [], artifacts)
        def metadata = new LocalComponentGraphResolveMetadata(
            id,
            componentId,
            "release",
            attributesSchema
        )

        def component = resolveStateFactory.realizedStateFor(metadata, [defaultVariant])
        return new TestComponent(component, defaultVariant)
    }

    TestComponent rootProject() {
        // TODO Shouldn't really be using the local component implementation here
        def componentId = newProjectId(":root")

        def rootVariant = createVariantMetadata("root", componentId, [], [])

        def metadata = new LocalComponentGraphResolveMetadata(
            newId("group", "root", "1.0"),
            componentId,
            "release",
            attributesSchema
        )

        def component = resolveStateFactory.realizedStateFor(metadata, [])
        return new TestComponent(component, rootVariant)
    }

    def createVariantMetadata(String name, ComponentIdentifier componentId, List<LocalOriginDependencyMetadata> dependencies, List<LocalComponentArtifactMetadata> artifacts) {
        def dependencyMetadata = new DefaultLocalVariantGraphResolveState.VariantDependencyMetadata(dependencies, [] as Set, [])

        CalculatedValue<ImmutableList<LocalComponentArtifactMetadata>> artifactMetadata =
            TestUtil.calculatedValueContainerFactory().create(Describables.of(name, "artifacts"),
                ImmutableList.copyOf(artifacts)
            )

        def artifactSets = ImmutableSet.of(
            new LocalVariantMetadata(
                name,
                new ComponentConfigurationIdentifier(componentId, name),
                Describables.of(name),
                attributes,
                ImmutableCapabilities.EMPTY,
                artifactMetadata
            )
        )

        def metadata = new DefaultLocalVariantGraphResolveMetadata(
            name, true, attributes, ImmutableCapabilities.EMPTY, false
        )

        return resolveStateFactory.realizedVariantStateFor(
            componentId, metadata, dependencyMetadata, artifactSets
        )
    }

    def traverses(Map<String, ?> args = [:], TestComponent from, TestComponent to) {
        def selector = dependsOn(args, from, to)
        selectorResolvesTo(selector, to.component.id, to.component.metadata.moduleVersionId)
        println "Traverse $from to ${to.component.id}"
        1 * metaDataResolver.resolve(to.component.id, _, _) >> { ComponentIdentifier id, ComponentOverrideMetadata requestMetaData, BuildableComponentResolveResult result ->
            println "Called ${to.component.id}"
            result.resolved(to.component, Stub(ComponentGraphSpecificResolveState))
        }
    }

    def doesNotTraverse(Map<String, ?> args = [:], TestComponent from, TestComponent to) {
        def selector = dependsOn(args, from, to)
        selectorResolvesTo(selector, to.component.id, to.component.metadata.moduleVersionId)
        0 * metaDataResolver.resolve(to.component.id, _, _)
    }

    def doesNotResolve(Map<String, ?> args = [:], TestComponent from, TestComponent to) {
        def selector = dependsOn(args, from, to)
        0 * idResolver.resolve(selector, _, _, _, _)
        0 * metaDataResolver.resolve(to.component.id, _, _)
    }

    def traversesMissing(Map<String, ?> args = [:], TestComponent from, TestComponent to) {
        def selector = dependsOn(args, from, to)
        selectorResolvesTo(selector, to.component.id, to.component.metadata.moduleVersionId)
        1 * metaDataResolver.resolve(to.component.id, _, _) >> { ComponentIdentifier id, ComponentOverrideMetadata requestMetaData, BuildableComponentResolveResult result ->
            result.notFound(to.component.id)
        }
    }

    def traversesBroken(Map<String, ?> args = [:], TestComponent from, TestComponent to) {
        def selector = dependsOn(args, from, to)
        selectorResolvesTo(selector, to.component.id, to.component.metadata.moduleVersionId)
        1 * metaDataResolver.resolve(to.component.id, _, _) >> { ComponentIdentifier id, ComponentOverrideMetadata requestMetaData, BuildableComponentResolveResult result ->
            org.gradle.internal.Factory<String> broken = { "broken" }
            result.failed(new ModuleVersionResolveException(newSelector(DefaultModuleIdentifier.newId("a", "b"), new DefaultMutableVersionConstraint("c")), broken))
        }
    }

    def brokenSelector(Map<String, ?> args = [:], TestComponent from, String to) {
        def selector = dependsOn(args, from, newId("group", to, "1.0"))
        1 * idResolver.resolve(selector, _, _, _, _) >> { ModuleComponentSelector sel, ComponentOverrideMetadata om, VersionSelector acceptor, VersionSelector rejector, BuildableComponentIdResolveResult result ->
            org.gradle.internal.Factory<String> broken = { "broken" }
            result.failed(new ModuleVersionResolveException(newSelector(DefaultModuleIdentifier.newId("a", "b"), new DefaultMutableVersionConstraint("c")), broken))
        }
    }

    ModuleComponentSelector dependsOn(Map<String, ?> args = [:], TestComponent from, TestComponent to) {
        dependsOn(args, from, to.component.metadata.moduleVersionId)
    }

    ModuleComponentSelector dependsOn(Map<String, ?> args = [:], TestComponent from, ModuleVersionIdentifier to) {
        ModuleVersionIdentifier dependencyId = args.revision ? newId(DefaultModuleIdentifier.newId(to.group, to.name), args.revision) : to
        boolean transitive = args.transitive == null || args.transitive
        boolean force = args.force
        ComponentSelector componentSelector = newSelector(DefaultModuleIdentifier.newId(dependencyId.group, dependencyId.name), new DefaultMutableVersionConstraint(dependencyId.version))
        List<ExcludeMetadata> excludeRules = []
        if (args.exclude) {
            ComponentGraphResolveState excluded = args.exclude.component
            excludeRules << new DefaultExclude(moduleIdentifierFactory.module(excluded.metadata.moduleVersionId.group, excluded.metadata.moduleVersionId.name))
        }
        def dependencyMetaData = new LocalComponentDependencyMetadata(
            componentSelector,
            "default",
            [] as List<IvyArtifactName>,
            excludeRules,
            force,
            false,
            transitive,
            false,
            false,
            null
        )
        from.variant.dependencies.add(dependencyMetaData)
        return componentSelector
    }

    def selectorResolvesTo(ComponentSelector selector, ComponentIdentifier id, ModuleVersionIdentifier mvId) {
        1 * idResolver.resolve(selector, _, _, _, _) >> { ComponentSelector sel, ComponentOverrideMetadata om, VersionSelector acceptor, VersionSelector rejector, BuildableComponentIdResolveResult result ->
            result.resolved(id, mvId)
        }
    }

    def ids(TestComponent... descriptors) {
        return descriptors.collect { it.component.metadata.moduleVersionId } as Set
    }

    class TestComponent {

        LocalComponentGraphResolveState component
        LocalVariantGraphResolveState variant

        TestComponent(
            LocalComponentGraphResolveState component,
            LocalVariantGraphResolveState variant
        ) {
            this.component = component
            this.variant = variant
        }

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

            throw new DefaultMultiCauseException("message", failures.values().collect {
                it.failure.withIncomingPaths(DependencyGraphPathResolver.calculatePaths(it.requiredBy, root, StandaloneDomainObjectContext.ANONYMOUS))
            })
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
