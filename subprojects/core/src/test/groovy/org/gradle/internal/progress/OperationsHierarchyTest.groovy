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

import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.atomic.AtomicLong

class OperationsHierarchyTest extends Specification {

    @Subject hierarchy = new OperationsHierarchy(new AtomicLong(), new LinkedList<Long>())

    def "does not have any identifier initially"() {
        when: hierarchy.currentOperationId()
        then: thrown(OperationsHierarchy.NoActiveOperationFound)

        when: hierarchy.completeCurrentOperation()
        then: thrown(OperationsHierarchy.NoActiveOperationFound)
    }

    def "provides operation identifier"() {
        def id1 = hierarchy.start()
        def id2 = hierarchy.start()

        expect:
        id1.id == 1
        id1.parentId == null

        id2.id == 1
        id2.parentId == null
    }

    def "provides hierarchical operation identifier"() {
        hierarchy = new OperationsHierarchy(new AtomicLong(2), [1L,2L] as LinkedList)

        when:
        def id = hierarchy.start()

        then:
        id.id == 3
        id.parentId == 2
    }

    def "provides current operation id"() {
        hierarchy = new OperationsHierarchy(new AtomicLong(2), [1L,2L] as LinkedList)

        when:
        def id1 = hierarchy.start()

        then:
        hierarchy.currentOperationId() //can be called many times
        id1.id == hierarchy.currentOperationId()
        id1.id == 3
    }

    def "removes current operation id"() {
        hierarchy = new OperationsHierarchy(new AtomicLong(12), [1L,2L] as LinkedList)

        when: hierarchy.start()

        then:
        hierarchy.currentOperationId() == 13
        hierarchy.completeCurrentOperation() == 13

        when: hierarchy.currentOperationId()

        then: thrown(OperationsHierarchy.NoActiveOperationFound)
    }

    def "new operations respect removed parents"() {
        hierarchy = new OperationsHierarchy(new AtomicLong(12), [1L,2L] as LinkedList)

        when:
        def id1 = hierarchy.start()
        def removed = hierarchy.completeCurrentOperation()
        def id2 = hierarchy.start()

        then:
        id1.id == 13
        id1.parentId == 2
        removed == 13
        id2.id == 14
        id2.parentId == 2
    }

    def "incomplete child operations are tolerated"() {
        def ids = [1L,2L] as LinkedList
        hierarchy = new OperationsHierarchy(new AtomicLong(12), ids)

        when: hierarchy.start()

        then: hierarchy.currentOperationId() == 13

        when: ids << 100L //some child operation is added

        then:
        hierarchy.currentOperationId() == 13 //current id is the same
        hierarchy.completeCurrentOperation() == 13

        when:
        def id = hierarchy.start()

        then:
        id.id == 14
        id.parentId == 100
    }

    def "reports if hierarchy is empty"() {
        def ids = [] as LinkedList
        hierarchy = new OperationsHierarchy(new AtomicLong(12), ids)
        hierarchy.start()
        ids.clear() //this should never happen

        when: hierarchy.completeCurrentOperation()
        then: thrown(OperationsHierarchy.HierarchyEmptyException)
    }
}
