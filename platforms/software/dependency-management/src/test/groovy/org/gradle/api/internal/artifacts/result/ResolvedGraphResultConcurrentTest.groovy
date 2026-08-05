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

package org.gradle.api.internal.artifacts.result

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.RootComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructure
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructureBuilder
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ResolvedGraphResultConcurrentTest extends ConcurrentSpec {

    private GatedGraphStructure structure
    private ResolvedGraphResult graph

    def setup() {
        // A graph with the shape: root -> a -> b
        GraphStructureBuilder builder = new GraphStructureBuilder()
        builder.addComponent(0, ComponentSelectionReasons.root(), null, Stub(RootComponentIdentifier), Stub(ModuleVersionIdentifier))
        builder.addNode(1, 0, ImmutableAttributes.EMPTY, ImmutableCapabilities.EMPTY, "root", -1)
        builder.addSuccessfulEdge(Stub(ModuleComponentSelector), false, 3)
        builder.addComponent(2, ComponentSelectionReasons.requested(), null, Stub(ModuleComponentIdentifier), Stub(ModuleVersionIdentifier))
        builder.addNode(3, 2, ImmutableAttributes.EMPTY, ImmutableCapabilities.EMPTY, "a-elements", -1)
        builder.addSuccessfulEdge(Stub(ModuleComponentSelector), false, 5)
        builder.addComponent(4, ComponentSelectionReasons.requested(), null, Stub(ModuleComponentIdentifier), Stub(ModuleVersionIdentifier))
        builder.addNode(5, 4, ImmutableAttributes.EMPTY, ImmutableCapabilities.EMPTY, "b-elements", -1)

        structure = new GatedGraphStructure(builder.build())
        graph = new ResolvedGraphResult(structure, null)
    }

    def "reading a component's variants and dependents concurrently does not deadlock"() {
        given:
        def rootComponent = graph.getComponent(0)
        def aComponent = graph.getComponent(1)

        def variantsReader = new AtomicReference<Thread>()
        def variants = new AtomicReference<List>()
        def dependents = new AtomicReference<Set>()

        when:
        async {
            start {
                dependents.set(aComponent.getDependents())
            }
            start {
                structure.edgesRead.await()
                variantsReader.set(Thread.currentThread())
                variants.set(aComponent.getVariants())
            }
            assert structure.edgesRead.await(10, TimeUnit.SECONDS)
            waitUntilParkedOrDone(variantsReader, variants)
            structure.resumeEdgesRead.countDown()
        }

        then:
        variants.get()*.displayName == ["a-elements"]
        def incomingEdges = dependents.get() as List
        incomingEdges.size() == 1
        incomingEdges[0].from.is(rootComponent)
        incomingEdges[0].selected.is(aComponent)

        and:
        rootComponent.dependencies.first().is(incomingEdges[0])
    }

    def "reading a component's dependencies and another component's dependents concurrently does not deadlock"() {
        given:
        def aComponent = graph.getComponent(1)
        def bComponent = graph.getComponent(2)

        def dependenciesReader = new AtomicReference<Thread>()
        def dependencies = new AtomicReference<Set>()
        def dependents = new AtomicReference<Set>()

        when:
        async {
            start {
                dependents.set(bComponent.getDependents())
            }
            start {
                structure.edgesRead.await()
                dependenciesReader.set(Thread.currentThread())
                dependencies.set(aComponent.getDependencies())
            }
            assert structure.edgesRead.await(10, TimeUnit.SECONDS)
            waitUntilParkedOrDone(dependenciesReader, dependencies)
            structure.resumeEdgesRead.countDown()
        }

        then:
        def aDependencies = dependencies.get() as List
        aDependencies.size() == 1
        aDependencies[0].from.is(aComponent)
        aDependencies[0].selected.is(bComponent)

        and:
        def incomingEdges = dependents.get() as List
        incomingEdges.size() == 1
        incomingEdges[0].is(aDependencies[0])
    }

    /**
     * Waits until the reader thread has finished or parked waiting for a lock or the gate —
     * BLOCKED for monitors, WAITING for {@code java.util.concurrent} synchronizers.
     */
    private static void waitUntilParkedOrDone(AtomicReference<Thread> threadRef, AtomicReference<?> result) {
        while (result.get() == null) {
            def thread = threadRef.get()
            if (thread != null && (!thread.alive || thread.state in [Thread.State.BLOCKED, Thread.State.WAITING])) {
                return
            }
            Thread.sleep(1)
        }
    }

    /**
     * Pauses the first thread to read {@link GraphStructure#edges()} until released, from inside
     * that thread's own computation. Reading a resolution result reaches edges() only when
     * computing a component's dependencies or the graph's incoming-edge index, so the gate
     * freezes those computations at a point where a lock-based implementation is holding its
     * guards, whatever kind of guards those are.
     */
    private static class GatedGraphStructure implements GraphStructure {
        private final GraphStructure delegate
        final CountDownLatch edgesRead = new CountDownLatch(1)
        final CountDownLatch resumeEdgesRead = new CountDownLatch(1)

        GatedGraphStructure(GraphStructure delegate) {
            this.delegate = delegate
        }

        @Override
        GraphStructure.Nodes nodes() {
            return delegate.nodes()
        }

        @Override
        GraphStructure.Components components() {
            return delegate.components()
        }

        @Override
        GraphStructure.Edges edges() {
            edgesRead.countDown()
            resumeEdgesRead.await()
            return delegate.edges()
        }
    }
}
