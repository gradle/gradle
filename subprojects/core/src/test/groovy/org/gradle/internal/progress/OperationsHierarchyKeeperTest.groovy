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



package org.gradle.internal.progress

import org.gradle.logging.ProgressLogger
import org.gradle.util.ConcurrentSpecification
import spock.lang.Subject

class OperationsHierarchyKeeperTest extends ConcurrentSpecification {

    @Subject manager = new OperationsHierarchyKeeper()

    def "provides hierarchy"() {
        def h1 = manager.currentHierarchy(null)
        def h2 = manager.currentHierarchy(null)

        expect:
        h1.hierarchy.is(h2.hierarchy)
        h1.sharedCounter.is(h2.sharedCounter)
    }

    def "provides hierarchy per thread"() {
        def h1
        def h2

        when:
        start { h1 = manager.currentHierarchy(null) }
        start { h2 = manager.currentHierarchy(null) }
        finished()

        then:
        !h1.hierarchy.is(h2.hierarchy)
        h1.sharedCounter.is(h2.sharedCounter)
    }

    def "may feed the parent logger"() {
        def parent1 = Stub(ProgressLogger) { currentOperationId() >> 1 }
        def parent2 = Mock(ProgressLogger) { currentOperationId() >> 2 }

        when:
        def h1 = manager.currentHierarchy(parent1)
        def h2 = manager.currentHierarchy(parent2)

        then:
        h1.hierarchy.is(h2.hierarchy)
        h1.hierarchy == [1]
    }
}
