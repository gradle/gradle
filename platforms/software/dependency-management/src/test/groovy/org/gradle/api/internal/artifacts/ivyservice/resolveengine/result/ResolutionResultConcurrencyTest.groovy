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

import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.internal.artifacts.result.ResolvedGraphResult

import java.lang.management.ManagementFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class ResolutionResultConcurrencyTest extends AbstractResolutionResultBuilderTest {

    def "reading a component's variants and dependents concurrently does not deadlock and returns the correct result"() {
        given: "a resolved graph where root depends on dep, and dep is read from two threads"
        def depNode = node(component("org", "dep", "1.0"))
        def root = rootNode("org", "root", "1.0")
        root.outgoingEdges >> [dep(depNode)]
        builder.start(root)
        builder.visitNode(root)
        builder.visitNode(depNode)
        builder.finish(root)

        def resolvedGraph = builder.getResolvedDependencyGraph([] as Set)
        def structure = resolvedGraph.graphSource().get()
        def graph = new ResolvedGraphResult(structure, resolvedGraph.availableVariantsByComponent())
        def depComponent = (0..<structure.components().count()).collect { graph.getComponent(it) }.find { it.id.displayName == "org:dep:1.0" }

        def graphMonitorHeld = new CountDownLatch(1)
        def variantsReaderParked = new CountDownLatch(1)
        def variants = new AtomicReference<List<ResolvedVariantResult>>()
        def dependents = new AtomicReference<Set<? extends ResolvedDependencyResult>>()

        def dependentsReader = new Thread({
            synchronized (graph) {
                graphMonitorHeld.countDown()
                variantsReaderParked.await()
                dependents.set(depComponent.getDependents())
            }
        }, "dependents-reader")

        def variantsReader = new Thread({
            graphMonitorHeld.await()
            variants.set(depComponent.getVariants())
        }, "variants-reader")

        when:
        dependentsReader.start()
        variantsReader.start()
        graphMonitorHeld.await()
        // Wait until the variants reader parks trying to acquire the graph monitor, then let the
        // dependents reader (holding the graph monitor) attempt to read the same component.
        waitUntilParkedOrDone(variantsReader)
        variantsReaderParked.countDown()
        failIfDeadlocked(variantsReader, dependentsReader)

        then: "both reads completed and returned dep's one variant and its single incoming edge from root"
        variants.get()*.owner*.displayName == ["org:dep:1.0"]
        dependents.get()*.from*.id*.displayName == ["org:root:1.0"]
        dependents.get()*.selected*.id*.displayName == ["org:dep:1.0"]

        cleanup:
        variantsReader?.interrupt()
        dependentsReader?.interrupt()
    }

    private static void waitUntilParkedOrDone(Thread thread) {
        while (thread.alive && thread.state != Thread.State.BLOCKED) {
            Thread.onSpinWait()
        }
    }

    private static void failIfDeadlocked(Thread... readers) {
        def threadMXBean = ManagementFactory.threadMXBean
        while (readers.any { it.alive }) {
            long[] deadlocked = threadMXBean.findDeadlockedThreads()
            if (deadlocked != null) {
                def dump = threadMXBean.getThreadInfo(deadlocked, true, true).join("\n")
                throw new AssertionError("Reading the resolution result deadlocked:\n" + dump as Object)
            }
            Thread.sleep(1)
        }
    }
}
