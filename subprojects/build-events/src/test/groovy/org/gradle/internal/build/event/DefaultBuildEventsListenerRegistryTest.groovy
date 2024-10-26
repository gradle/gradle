/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.build.event

import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.build.event.types.DefaultTaskDescriptor
import org.gradle.internal.build.event.types.DefaultTaskFailureResult
import org.gradle.internal.build.event.types.DefaultTaskFinishedProgressEvent
import org.gradle.internal.build.event.types.DefaultTaskSkippedResult
import org.gradle.internal.build.event.types.DefaultTaskSuccessResult
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.DefaultBuildOperationListenerManager
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.internal.service.scopes.Scope
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult

import static org.gradle.internal.time.TestTime.timestampOf

class DefaultBuildEventsListenerRegistryTest extends ConcurrentSpec {
    def factory = new MockBuildEventListenerFactory()
    def listenerManager = new DefaultListenerManager(Scope.Build)
    def buildOperationListenerManager = new DefaultBuildOperationListenerManager()
    def gradle = Stub(GradleInternal) {
        isRootBuild() >> true
    }
    def buildResult = new BuildResult(gradle, null)
    def registry = new DefaultBuildEventsListenerRegistry(factory, listenerManager, buildOperationListenerManager, executorFactory)

    def cleanup() {
        // Signal the end of the build, to stop everything
        signalBuildFinished()
    }

    def "listener can receive task finish events"() {
        def listener = Mock(OperationCompletionListener)
        def provider = Providers.of(listener)
        def success = taskFinishEvent()
        def failure = failedTaskFinishEvent()
        def skipped = skippedTaskFinishEvent()

        when:
        registry.onTaskCompletion(provider)

        then:
        registry.subscriptions.size() == 1
        0 * listener._

        when:
        async {
            factory.fire(success)
            factory.fire(failure)
            factory.fire(skipped)
            signalBuildFinished()
        }

        then:
        1 * listener.onFinish({ it instanceof TaskFinishEvent && it.result instanceof TaskSuccessResult })
        1 * listener.onFinish({ it instanceof TaskFinishEvent && it.result instanceof TaskFailureResult })
        1 * listener.onFinish({ it instanceof TaskFinishEvent && it.result instanceof TaskSkippedResult })
        0 * listener._
    }

    def "listener can receive build operation finish events"() {
        def listener = Mock(BuildOperationListener)
        def provider = Providers.of(listener)
        def descriptor = descriptor()
        def finishEvent = operationFinishEvent()
        def broadcaster = buildOperationListenerManager.broadcaster

        when:
        registry.onOperationCompletion(provider)

        then:
        registry.subscriptions.size() == 1
        0 * listener._

        when:
        async {
            broadcaster.started(descriptor, startOperationEvent())
            broadcaster.progress(Stub(OperationIdentifier), operationProgressEvent())
            broadcaster.finished(descriptor, finishEvent)
            signalBuildFinished()
        }

        then:
        1 * listener.finished(descriptor, finishEvent)
        0 * listener._
    }

    def "does nothing when listener is already subscribed"() {
        def listener = Mock(OperationCompletionListener)
        def provider = Providers.of(listener)

        when:
        registry.onTaskCompletion(provider)
        registry.onTaskCompletion(provider)

        then:
        registry.subscriptions.size() == 1

        cleanup:
        signalBuildFinished()
    }

    def "listeners receive events concurrently"() {
        def listener1 = {
            thread.blockUntil.received
            instant.handled
        } as OperationCompletionListener
        def listener2 = {
            instant.received
            thread.blockUntil.handled
        } as OperationCompletionListener

        when:
        registry.onTaskCompletion(Providers.of(listener1))
        registry.onTaskCompletion(Providers.of(listener2))
        async {
            factory.fire(taskFinishEvent())
            thread.blockUntil.handled
            signalBuildFinished()
        }

        then:
        instant.received < instant.handled
    }

    def "broken listener is quarantined and failure rethrown at completion of build"() {
        def failure = new RuntimeException()
        def brokenListener = Mock(OperationCompletionListener)
        def okListener = Mock(OperationCompletionListener)

        when:
        registry.onTaskCompletion(Providers.of(brokenListener))
        registry.onTaskCompletion(Providers.of(okListener))
        async {
            factory.fire(taskFinishEvent())
            thread.blockUntil.handled
            factory.fire(taskFinishEvent())
            signalBuildFinished()
        }

        then:
        1 * brokenListener.onFinish(_) >> {
            instant.handled
            throw failure
        }
        2 * okListener.onFinish(_)
        0 * brokenListener._
        0 * okListener._

        and:
        def e = thrown(RuntimeException)
        e.is(failure)
    }

    private signalBuildFinished() {
        listenerManager.getBroadcaster(BuildListener).buildFinished(buildResult)
    }

    private DefaultTaskFinishedProgressEvent taskFinishEvent() {
        new DefaultTaskFinishedProgressEvent(123L, Stub(DefaultTaskDescriptor), Stub(DefaultTaskSuccessResult))
    }

    private DefaultTaskFinishedProgressEvent failedTaskFinishEvent() {
        new DefaultTaskFinishedProgressEvent(123L, Stub(DefaultTaskDescriptor), Stub(DefaultTaskFailureResult))
    }

    private DefaultTaskFinishedProgressEvent skippedTaskFinishEvent() {
        new DefaultTaskFinishedProgressEvent(123L, Stub(DefaultTaskDescriptor), Stub(DefaultTaskSkippedResult))
    }

    private OperationStartEvent startOperationEvent() {
        new OperationStartEvent(timestampOf(123))
    }

    private OperationProgressEvent operationProgressEvent() {
        new OperationProgressEvent(timestampOf(123), null)
    }

    private OperationFinishEvent operationFinishEvent() {
        new OperationFinishEvent(timestampOf(123), timestampOf(345), null, null)
    }

    private BuildOperationDescriptor descriptor() {
        new BuildOperationDescriptor(Stub(OperationIdentifier), null, "name", "name", "name", null, null, 12)
    }

    class MockBuildEventListenerFactory implements BuildEventListenerFactory {
        private List<BuildEventConsumer> consumers = []

        def fire(Object event) {
            consumers.forEach {
                it.dispatch(event)
            }
        }

        @Override
        Iterable<Object> createListeners(BuildEventSubscriptions subscriptions, BuildEventConsumer consumer) {
            consumers.add(consumer)
            return []
        }
    }
}
