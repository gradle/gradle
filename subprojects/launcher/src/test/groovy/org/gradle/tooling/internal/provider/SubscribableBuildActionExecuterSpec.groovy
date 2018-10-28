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
import org.gradle.initialization.BuildRequestContext
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.exec.BuildActionExecuter
import org.gradle.launcher.exec.BuildActionParameters
import spock.lang.Specification

class SubscribableBuildActionExecuterSpec extends Specification {


    def testRemovesListenersOnStop() throws IOException {
        given:
        BuildActionExecuter<BuildActionParameters> buildActionExecuter = Mock(BuildActionExecuter)
        BuildOperationListenerManager buildOperationService = Mock(BuildOperationListenerManager)
        SubscribableBuildAction buildAction = subscribableBuildAction() //Mock(SubscribableBuildAction)
        BuildRequestContext buildRequestContext = Mock(BuildRequestContext)
        BuildActionParameters buildActionParameters = Mock(BuildActionParameters)
        ServiceRegistry serviceRegistry = Mock(ServiceRegistry)
        SubscribableBuildActionRunnerRegistration registration = Mock(SubscribableBuildActionRunnerRegistration)
        BuildEventConsumer consumer = Mock(BuildEventConsumer)
        BuildOperationListener listener1 = Mock(BuildOperationListener)
        BuildOperationListener listener2 = Mock(BuildOperationListener)

        _ * buildRequestContext.eventConsumer >> consumer
        _ * registration.createListeners(_, consumer) >> [listener1, listener2]

        def runner = new SubscribableBuildActionExecuter(buildActionExecuter, buildOperationService, [registration])

        when:
        runner.execute(buildAction, buildRequestContext, buildActionParameters, serviceRegistry)

        then:
        1 * buildOperationService.addListener(listener1)
        1 * buildOperationService.addListener(listener2)
        1 * buildOperationService.removeListener(listener1)
        1 * buildOperationService.removeListener(listener2)
        0 * buildOperationService._
    }

    SubscribableBuildAction subscribableBuildAction() {
        SubscribableBuildAction buildAction = Mock()
        BuildClientSubscriptions clientSubscriptions = Mock()
        _ * clientSubscriptions.isSendBuildProgressEvents() >> true
        _ * clientSubscriptions.isSendTaskProgressEvents() >> true
        _ * clientSubscriptions.isSendTestProgressEvents() >> true
        _ * buildAction.getClientSubscriptions() >> clientSubscriptions
        buildAction
    }
}
