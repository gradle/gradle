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

import spock.lang.Specification
import org.gradle.tooling.BuildAction
import org.gradle.tooling.IntermediateResultHandler
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor

class DefaultPhasedBuildActionExecuterBuilderTest extends Specification {
    def connection = Stub(AsyncConsumerActionExecutor)
    def parameters = Stub(ConnectionParameters)
    def builder = new DefaultBuildActionExecuter.Builder(connection, parameters)

    def "phased build action built correctly"() {
        def projectsLoadedAction = Mock(BuildAction)
        def projectsLoadedHandler = Mock(IntermediateResultHandler)
        def buildFinishedAction = Mock(BuildAction)
        def buildFinishedHandler = Mock(IntermediateResultHandler)

        when:
        def executer = builder.projectsLoaded(projectsLoadedAction, projectsLoadedHandler)
            .buildFinished(buildFinishedAction, buildFinishedHandler)
            .build()

        then:
        executer.connection == connection
        executer.connectionParameters == parameters
        executer.phasedBuildAction.getProjectsLoadedAction().getAction() == projectsLoadedAction
        executer.phasedBuildAction.getProjectsLoadedAction().getHandler() == projectsLoadedHandler
        executer.phasedBuildAction.getBuildFinishedAction().getAction() == buildFinishedAction
        executer.phasedBuildAction.getBuildFinishedAction().getHandler() == buildFinishedHandler
    }

    def "exception when multiple actions per phase"() {
        when:
        builder.projectsLoaded(Stub(BuildAction), Stub(IntermediateResultHandler))
        builder.projectsLoaded(Stub(BuildAction), Stub(IntermediateResultHandler))

        then:
        IllegalArgumentException e1 = thrown()
        e1.message == 'ProjectsLoadedAction has already been added. Only one action per phase is allowed.'

        when:
        builder.buildFinished(Stub(BuildAction), Stub(IntermediateResultHandler))
        builder.buildFinished(Stub(BuildAction), Stub(IntermediateResultHandler))

        then:
        IllegalArgumentException e2 = thrown()
        e2.message == 'BuildFinishedAction has already been added. Only one action per phase is allowed.'
    }
}
