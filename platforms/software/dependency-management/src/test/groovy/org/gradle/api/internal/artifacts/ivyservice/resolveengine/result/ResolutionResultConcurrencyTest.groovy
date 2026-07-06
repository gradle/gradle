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

import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal
import org.gradle.api.internal.artifacts.result.ResolvedGraphResult
import spock.lang.Timeout

import java.lang.management.ManagementFactory
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

class ResolutionResultConcurrencyTest extends AbstractResolutionResultBuilderTest {

    @Timeout(30)
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
        def graph = new ResolvedGraphResult(resolvedGraph.graphSource().get(), resolvedGraph.availableVariantsByComponent())
        def depComponent = componentByDisplayName(graph, "org:dep:1.0")

        def pool = Executors.newFixedThreadPool(2)
        def graphMonitorHeld = new CountDownLatch(1)
        def variantsReaderParked = new CountDownLatch(1)
        def variantsReaderThread = new AtomicReference<Thread>()

        when:
        def dependents = pool.submit({
            synchronized (graph) {
                graphMonitorHeld.countDown()
                variantsReaderParked.await()
                depComponent.getDependents()
            }
        } as Callable)

        def variants = pool.submit({
            variantsReaderThread.set(Thread.currentThread())
            graphMonitorHeld.await()
            depComponent.getVariants()
        } as Callable)

        graphMonitorHeld.await()
        // Let the variants reader park trying to acquire the graph monitor (while holding the
        // component monitor), then release the dependents reader to read the same component.
        waitUntilParked(variantsReaderThread)
        variantsReaderParked.countDown()
        awaitCompletionOrFailOnDeadlock(dependents, variants)

        then: "both reads returned dep's one variant and its single incoming edge from root"
        def readVariants = variants.get()
        def readDependents = dependents.get()
        readVariants*.owner*.displayName == ["org:dep:1.0"]
        readDependents*.from*.id*.displayName == ["org:root:1.0"]
        readDependents*.selected*.id*.displayName == ["org:dep:1.0"]

        cleanup:
        pool.shutdownNow()
    }

    private static ResolvedComponentResultInternal componentByDisplayName(ResolvedGraphResult graph, String displayName) {
        def components = graph.structure().components()
        def index = (0..<components.count()).find { components.id(it).displayName == displayName }
        graph.getComponent(index)
    }

    private static void waitUntilParked(AtomicReference<Thread> threadRef) {
        while (true) {
            def thread = threadRef.get()
            if (thread != null && (!thread.alive || thread.state == Thread.State.BLOCKED)) {
                return
            }
            Thread.onSpinWait()
        }
    }

    private static void awaitCompletionOrFailOnDeadlock(Future<?>... readers) {
        def threadMXBean = ManagementFactory.threadMXBean
        while (readers.any { !it.done }) {
            long[] deadlocked = threadMXBean.findDeadlockedThreads()
            if (deadlocked != null) {
                def dump = threadMXBean.getThreadInfo(deadlocked, true, true).join("\n")
                throw new AssertionError("Reading the resolution result deadlocked:\n" + dump as Object)
            }
            Thread.sleep(1)
        }
    }
}
