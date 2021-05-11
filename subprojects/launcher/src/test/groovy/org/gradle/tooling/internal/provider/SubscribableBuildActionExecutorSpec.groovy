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

package org.gradle.tooling.internal.provider

import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.build.event.BuildEventListenerFactory
import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.session.BuildSessionContext
import org.gradle.internal.session.BuildSessionActionExecutor
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.internal.provider.action.SubscribableBuildAction
import spock.lang.Specification

class SubscribableBuildActionExecutorSpec extends Specification {

    def "removes listeners after executing build action"() {
        given:
        def delegate = Mock(BuildSessionActionExecutor)
        def listenerManager = Mock(ListenerManager)
        def buildOperationListenerManager = Mock(BuildOperationListenerManager)
        def buildAction = Stub(SubscribableBuildAction) {
            getClientSubscriptions() >> new BuildEventSubscriptions(EnumSet.allOf(OperationType))
        }
        def consumer = Stub(BuildEventConsumer)
        def buildSessionContext = Stub(BuildSessionContext)

        def listener1 = Stub(BuildOperationListener)
        def listener2 = Stub(BuildOperationListener)
        def factory = Stub(BuildEventListenerFactory) {
            createListeners(_, consumer) >> [listener1, listener2]
        }

        def runner = new SubscribableBuildActionExecutor(listenerManager, buildOperationListenerManager, factory, consumer, delegate)

        when:
        runner.execute(buildAction, buildSessionContext)

        then:
        1 * listenerManager.addListener(listener1)
        1 * listenerManager.addListener(listener2)
        1 * buildOperationListenerManager.addListener(listener1)
        1 * buildOperationListenerManager.addListener(listener2)

        then:
        1 * delegate.execute(buildAction, buildSessionContext)

        then:
        1 * listenerManager.removeListener(listener1)
        1 * listenerManager.removeListener(listener2)
        1 * buildOperationListenerManager.removeListener(listener1)
        1 * buildOperationListenerManager.removeListener(listener2)
        0 * _
    }

}
