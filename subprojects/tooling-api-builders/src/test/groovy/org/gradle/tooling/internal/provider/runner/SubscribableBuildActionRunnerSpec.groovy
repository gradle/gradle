/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner

import org.gradle.api.internal.GradleInternal
import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.invocation.BuildActionRunner
import org.gradle.internal.invocation.BuildController
import org.gradle.internal.progress.BuildOperationService
import org.gradle.internal.service.ServiceRegistry
import org.gradle.tooling.internal.provider.BuildClientSubscriptions
import org.gradle.tooling.internal.provider.SubscribableBuildAction
import org.gradle.tooling.internal.provider.SubscribableBuildActionRunner
import spock.lang.Specification

class SubscribableBuildActionRunnerSpec extends Specification {


    def testRemovesListenersOnStop() throws IOException {
        given:
        BuildActionRunner buildActionRunner = Mock(BuildActionRunner)
        BuildOperationService buildOperationService = Mock(BuildOperationService)
        SubscribableBuildAction buildAction = subscribableBuildAction() //Mock(SubscribableBuildAction)
        BuildController buildController = buildController() //Mock(BuildController)

        def runner = new SubscribableBuildActionRunner(buildActionRunner, buildOperationService, [new ToolingApiSubscribableBuildActionRunnerRegistration()]);

        when:
        runner.run(buildAction, buildController)

        then:
        2 * buildOperationService.addListener(_)
        2 * buildOperationService.removeListener(_)
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

    BuildController buildController() {
        BuildController buildController = Mock()
        GradleInternal gradleInternal = Mock()
        ServiceRegistry gradleServices = Mock()
        _ * gradleServices.get(BuildEventConsumer) >> Mock(BuildEventConsumer)
        _ * gradleInternal.getServices() >> gradleServices
        _ * buildController.getGradle() >> gradleInternal
        buildController
    }
}
