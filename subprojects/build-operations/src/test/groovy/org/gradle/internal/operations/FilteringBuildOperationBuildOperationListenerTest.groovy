/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.operations

import spock.lang.Specification

class FilteringBuildOperationBuildOperationListenerTest extends Specification {
    def delegate = Mock(BuildOperationListener)
    def result = Mock(Object)
    def progressDetails = Mock(Object)
    def startEvent = new OperationStartEvent(0L)
    def progressEvent = new OperationProgressEvent(50L, progressDetails)
    def finishEvent = new OperationFinishEvent(0L, 100L, null, result)

    def "passes non-filtered events through directly"() {
        given:
        def operationId = id(1)
        def operation = BuildOperationDescriptor
            .displayName("test")
            .build(operationId, null)
        def listener = createListener { true }

        when:
        listener.started(operation, startEvent)
        then:
        1 * delegate.started(operation, startEvent)
        0 * _

        when:
        listener.progress(operationId, progressEvent)
        then:
        1 * delegate.progress(operationId, progressEvent)
        0 * _

        when:
        listener.finished(operation, finishEvent)
        then:
        1 * delegate.finished(operation, finishEvent)
        0 * _
    }

    def "blocks filtered events"() {
        given:
        def filteredOperationId = id(1)
        def filteredOperation = BuildOperationDescriptor
            .displayName("filtered")
            .build(filteredOperationId, null)
        def listener = createListener { false }

        when:
        listener.started(filteredOperation, startEvent)
        then:
        0 * _

        when:
        listener.progress(filteredOperationId, progressEvent)
        then:
        0 * _

        when:
        listener.finished(filteredOperation, finishEvent)
        then:
        0 * _
    }

    def "re-parents event successfully"() {
        given:
        def rootId = id(1)
        def root = BuildOperationDescriptor
            .displayName("root")
            .build(rootId, null)
        def filteredParentId = id(2)
        def filteredParent = BuildOperationDescriptor
            .displayName("filtered-parent")
            .build(filteredParentId, rootId)
        def childId = id(3)
        def child = BuildOperationDescriptor
            .displayName("child")
            .build(childId, filteredParentId)
        def grandChildId = id(4)
        def grandChild = BuildOperationDescriptor
            .displayName("grand-child")
            .build(grandChildId, childId)
        def listener = createListener { it.id != filteredParentId }

        when:
        listener.started(root, startEvent)
        then:
        1 * delegate.started(root, startEvent)
        0 * _

        when:
        listener.progress(rootId, progressEvent)
        then:
        1 * delegate.progress(rootId, progressEvent)
        0 * _

        when:
        listener.started(filteredParent, startEvent)
        then:
        0 * _

        when:
        listener.progress(filteredParentId, progressEvent)
        then:
        0 * _

        when:
        listener.started(child, startEvent)
        then:
        1 * delegate.started(_ as BuildOperationDescriptor, startEvent) >> { BuildOperationDescriptor buildOperation, OperationStartEvent startEvent ->
            assert buildOperation.parentId == rootId
        }
        0 * _

        when:
        listener.progress(childId, progressEvent)
        then:
        1 * delegate.progress(childId, progressEvent)
        0 * _

        when:
        listener.started(grandChild, startEvent)
        then:
        1 * delegate.started(grandChild, startEvent)
        0 * _

        when:
        listener.progress(grandChildId, progressEvent)
        then:
        1 * delegate.progress(grandChildId, progressEvent)
        0 * _

        when:
        listener.finished(grandChild, finishEvent)
        then:
        1 * delegate.finished(grandChild, finishEvent)
        0 * _

        when:
        listener.finished(child, finishEvent)
        then:
        1 * delegate.finished(_ as BuildOperationDescriptor, finishEvent) >> { BuildOperationDescriptor buildOperation, OperationFinishEvent startEvent ->
            assert buildOperation.parentId == rootId
        }
        0 * _

        when:
        listener.finished(filteredParent, finishEvent)
        then:
        0 * _

        when:
        listener.finished(root, finishEvent)
        then:
        1 * delegate.finished(root, finishEvent)
        0 * _
    }

    def "re-parents if root is filtered"() {
        def filteredRootId = id(1)
        def filteredRoot = BuildOperationDescriptor
            .displayName("filtered-root")
            .build(filteredRootId, null)
        def childId = id(2)
        def child = BuildOperationDescriptor
            .displayName("child")
            .build(childId, filteredRootId)

        def listener = createListener { it.id != filteredRootId }

        when:
        listener.started(filteredRoot, startEvent)
        then:
        0 * _

        when:
        listener.started(child, startEvent)
        then:
        1 * delegate.started(_ as BuildOperationDescriptor, startEvent) >> { BuildOperationDescriptor buildOperation, OperationStartEvent startEvent ->
            assert buildOperation.parentId == null
        }
        0 * _

        when:
        listener.finished(child, finishEvent)
        then:
        1 * delegate.finished(_ as BuildOperationDescriptor, finishEvent) >> { BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent ->
            assert buildOperation.parentId == null
        }
        0 * _

        when:
        listener.finished(filteredRoot, finishEvent)
        then:
        0 * _
    }

    private createListener(FilteringBuildOperationBuildOperationListener.Filter filter) {
        new FilteringBuildOperationBuildOperationListener(delegate, filter)
    }

    private static id(long id) {
        new OperationIdentifier(id)
    }
}
