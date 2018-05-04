/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.scheduler

import com.google.common.collect.Sets

class DefaultSchedulerParallelTest extends AbstractSchedulerTest {

    Scheduler scheduler = new DefaultScheduler(graph, false, new ParallelWorkerPool(4))

    def "schedules many tasks at once"() {
        given:
        def a = task(project: "a", "task")
        def b = task(project: "b", "task")
        def c = task(project: "c", "task")
        def d = task(project: "d", "task")
        def e = task(project: "e", "task")
        def f = task(project: "f", "task")
        def g = task(project: "g", "task")
        def h = task(project: "h", "task")

        expect:
        executesBatches([a, b, c, d, e, f, g, h])
    }

    def "schedules many tasks in dependency order"() {
        given:
        def a = task(project: "a", "task")
        def b = task(project: "b", "task")
        def c = task(project: "c", "task")
        def d = task(project: "d", "task")
        def e = task(project: "e", "task", dependsOn: [a, b, c, d])
        def f = task(project: "f", "task", dependsOn: [a, b, c, d])
        def g = task(project: "g", "task", dependsOn: [a, b, c, d])
        def h = task(project: "h", "task", dependsOn: [a, b, c, d])

        expect:
        executesBatches([a, b, c, d], [e, f, g, h])
    }

    void executesBatches(List<Node>... batches) {
        executeGraph()
        def actualNodes = new ArrayDeque<Node>(executedNodes)
        def expectedBatches = new ArrayDeque<List<Node>>(batches as List)
        while (!expectedBatches.isEmpty()) {
            def expectedBatch = expectedBatches.remove()
            assert actualNodes.size() >= expectedBatch.size()
            def actualBatch = Sets.newLinkedHashSet()
            expectedBatch.size().times {
                actualBatch.add(actualNodes.remove())
            }
            assert actualBatch == (expectedBatch as Set)
        }
        assert actualNodes.isEmpty()
    }
}
