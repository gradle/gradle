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

package org.gradle.tooling.internal.consumer

import org.gradle.testing.internal.util.Specification
import org.gradle.tooling.BuildAction
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.exceptions.MultipleBuildActionsException
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor

class DefaultPhasedBuildActionExecuterBuilderTest extends Specification {
    def connection = Mock(AsyncConsumerActionExecutor)
    def parameters = Mock(ConnectionParameters)
    def builder = new DefaultPhasedBuildActionExecuter.Builder(connection, parameters)

    def "phased build action built correctly"() {
        def afterLoadingAction = Mock(BuildAction)
        def afterLoadingHandler = Mock(ResultHandler)
        def afterConfigurationAction = Mock(BuildAction)
        def afterConfigurationHandler = Mock(ResultHandler)
        def afterBuildAction = Mock(BuildAction)
        def afterBuildHandler = Mock(ResultHandler)

        when:
        def executer = builder.addAfterLoadingAction(afterLoadingAction, afterLoadingHandler)
            .addAfterConfigurationAction(afterConfigurationAction, afterConfigurationHandler)
            .addAfterBuildAction(afterBuildAction, afterBuildHandler)
            .build()

        then:
        executer.connection == connection
        executer.connectionParameters == parameters
        executer.phasedBuildAction.getAfterLoadingAction().getAction() == afterLoadingAction
        executer.phasedBuildAction.getAfterLoadingAction().getHandler() == afterLoadingHandler
        executer.phasedBuildAction.getAfterConfigurationAction().getAction() == afterConfigurationAction
        executer.phasedBuildAction.getAfterConfigurationAction().getHandler() == afterConfigurationHandler
        executer.phasedBuildAction.getAfterBuildAction().getAction() == afterBuildAction
        executer.phasedBuildAction.getAfterBuildAction().getHandler() == afterBuildHandler
    }

    def "exception when multiple actions per phase"() {
        when:
        builder.addAfterLoadingAction(Mock(BuildAction), Mock(ResultHandler))
        builder.addAfterLoadingAction(Mock(BuildAction), Mock(ResultHandler))

        then:
        MultipleBuildActionsException e1 = thrown()
        e1.message == 'AfterLoadingAction has already been added. Only one action per phase is allowed.'

        when:
        builder.addAfterConfigurationAction(Mock(BuildAction), Mock(ResultHandler))
        builder.addAfterConfigurationAction(Mock(BuildAction), Mock(ResultHandler))

        then:
        MultipleBuildActionsException e2 = thrown()
        e2.message == 'AfterConfigurationAction has already been added. Only one action per phase is allowed.'

        when:
        builder.addAfterBuildAction(Mock(BuildAction), Mock(ResultHandler))
        builder.addAfterBuildAction(Mock(BuildAction), Mock(ResultHandler))

        then:
        MultipleBuildActionsException e3 = thrown()
        e3.message == 'AfterBuildAction has already been added. Only one action per phase is allowed.'
    }
}
