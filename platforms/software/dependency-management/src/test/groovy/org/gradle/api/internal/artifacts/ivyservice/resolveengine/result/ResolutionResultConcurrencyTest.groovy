/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.api.artifacts.result.ComponentSelectionReason
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal
import org.gradle.api.internal.artifacts.result.ResolvedGraphResult
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.cache.internal.Store
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.LocalVariantGraphResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.lang.management.ManagementFactory
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Supplier

/**
 * Verifies that the lazily-computed {@link ResolvedGraphResult} /
 * {@link org.gradle.api.internal.artifacts.result.DefaultResolvedComponentResult} API can be
 * read concurrently without deadlocking.
 *
 * <p>The build scan and github-dependency-graph plugins read the resolution result from build
 * operation listeners, which run without holding the project lock and therefore may read the
 * same result graph in parallel. Two access paths lazily compute values while holding a monitor
 * and then call into the other object, and previously acquired these two monitors in opposite
 * orders:
 * <ul>
 *     <li>{@code getVariants()} locks the component, then calls {@code graph.getVariant()}.</li>
 *     <li>{@code getDependents()} locks the graph (in {@code getIncomingEdges()}), then calls
 *     {@code component.getDependencies()}, which locks the component.</li>
 * </ul>
 * Racing these two paths could deadlock.
 */
class ResolutionResultConcurrencyTest extends Specification {

    class DummyStore implements Store<GraphStructure> {
        GraphStructure load(Supplier<GraphStructure> createIfNotPresent) {
            return createIfNotPresent.get()
        }
    }

    def builder = new StreamingResolutionResultBuilder(
        new DummyBinaryStore(),
        new DummyStore(),
        new ThisBuildTreeOnlyGraphElementStore(),
        new AttributeDesugaring(AttributeTestUtil.attributesFactory()),
        new CapabilitySelectorSerializer(),
        DependencyManagementTestUtil.componentSelectionDescriptorFactory(),
        new DefaultImmutableModuleIdentifierFactory(),
        AttributeTestUtil.attributesFactory(),
        TestUtil.objectInstantiator(),
        false
    )

    int nodeIds = 0
    int componentIds = 0

    def "concurrent reads of variants and dependents do not deadlock"() {
        given: "a resolved graph with a root depending on two other components"
        def dep1 = node(component("org", "dep1", "1.0"))
        def dep2 = node(component("org", "dep2", "1.0"))
        def root = rootNode("org", "root", "1.0")
        root.outgoingEdges >> [dep(dep1), dep(dep2)]

        builder.start(root)
        builder.visitNode(root)
        builder.visitNode(dep1)
        builder.visitNode(dep2)
        builder.finish(root)

        def resolvedGraph = builder.getResolvedDependencyGraph([] as Set)
        def structure = resolvedGraph.graphSource().get()
        def componentCount = structure.components().count()

        def executor = Executors.newFixedThreadPool(2)

        when: "the variants and dependents of every component are read in parallel on a fresh, uncached graph"
        // The caches are one-shot: once populated the racy compute paths are never re-entered.
        // So each iteration wraps the (immutable) structure in a fresh ResolvedGraphResult and
        // aligns the two reader threads on a barrier to maximise the chance of interleaving.
        200.times {
            def graph = new ResolvedGraphResult(structure, resolvedGraph.availableVariantsByComponent())
            def barrier = new CyclicBarrier(2)

            Future<?> variantsReader = executor.submit({
                barrier.await()
                for (int i = 0; i < componentCount; i++) {
                    graph.getComponent(i).getVariants()
                }
            } as Callable)

            Future<?> dependentsReader = executor.submit({
                barrier.await()
                for (int i = 0; i < componentCount; i++) {
                    (graph.getComponent(i) as ResolvedComponentResultInternal).getDependents()
                }
            } as Callable)

            awaitOrReportDeadlock(variantsReader, dependentsReader)
        }

        then:
        noExceptionThrown()

        cleanup:
        executor.shutdownNow()
    }

    private static void awaitOrReportDeadlock(Future<?>... futures) {
        try {
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS)
            }
        } catch (TimeoutException ignored) {
            def deadlocked = ManagementFactory.threadMXBean.findDeadlockedThreads()
            def detail = deadlocked == null
                ? "reader threads did not complete within the timeout"
                : "deadlocked threads: " + ManagementFactory.threadMXBean.getThreadInfo(deadlocked, true, true)
            throw new AssertionError("Concurrent read of the resolution result deadlocked: " + detail as Object)
        }
    }

    private DependencyGraphComponent component(String org, String name, String ver, ComponentSelectionReason reason = ComponentSelectionReasons.requested()) {
        def componentId = componentIds++
        def componentMetadata = Stub(ComponentGraphResolveMetadata) {
            getModuleVersionId() >> DefaultModuleVersionIdentifier.newId(DefaultModuleIdentifier.newId(org, name), ver)
        }

        def componentState = Stub(ComponentGraphResolveState) {
            getInstanceId() >> componentId
            getId() >> DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(org, name), ver)
            getMetadata() >> componentMetadata
        }

        return Stub(DependencyGraphComponent) {
            getResultId() >> componentId
            getSelectionReason() >> reason
            getResolveState() >> componentState
            getSelectedVariants() >> []
        }
    }

    private DependencyGraphEdge dep(DependencyGraphNode node) {
        def moduleVersionId = node.owner.resolveState.metadata.moduleVersionId
        def selector = selector(moduleVersionId.group, moduleVersionId.name, moduleVersionId.version)
        def edge = Stub(DependencyGraphEdge)
        _ * edge.requested >> selector.requested
        _ * edge.selector >> selector
        _ * edge.failure >> null
        _ * edge.targetNodes >> [node]
        return edge
    }

    private DependencyGraphNode node(DependencyGraphComponent component) {
        int nodeId = nodeIds++
        def node = Stub(DependencyGraphNode) {
            getOwner() >> component
            getNodeId() >> nodeId
            getExternalVariant() >> null
        }
        component.selectedVariants.add(node)
        return node
    }

    private RootGraphNode rootNode(String org, String name, String ver) {
        def component = component(org, name, ver, ComponentSelectionReasons.root())
        int nodeId = nodeIds++
        def node = Stub(RootGraphNode) {
            getOwner() >> component
            getNodeId() >> nodeId
            getExternalVariant() >> null
            getMetadata() >> Mock(LocalVariantGraphResolveMetadata) {
                getAttributes() >> AttributeTestUtil.attributes(["org.foo": "v1", "org.bar": 2, "org.baz": true])
            }
        }
        component.selectedVariants.add(node)
        return node
    }

    private DependencyGraphSelector selector(String org, String name, String ver) {
        def selector = Stub(DependencyGraphSelector)
        selector.requested >> DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(org, name), new DefaultMutableVersionConstraint(ver))
        return selector
    }
}
