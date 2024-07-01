/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.session

import org.gradle.api.internal.StartParameterInternal
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.BuildClientMetaData
import org.gradle.initialization.BuildEventConsumer
import org.gradle.initialization.BuildRequestMetaData
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry
import org.gradle.internal.service.scopes.Scope
import spock.lang.Specification

import java.util.function.Function

class BuildSessionStateTest extends Specification {
    def listenerManager = new DefaultListenerManager(Scope.BuildSession)
    def actionExecutor = Mock(BuildSessionActionExecutor)
    def userHomeServiceRegistry = Mock(GradleUserHomeScopeServiceRegistry)
    def crossBuildState = Mock(CrossBuildSessionState)
    def startParameter = new StartParameterInternal()
    def buildRequestMetadata = Mock(BuildRequestMetaData)
    def clientMetadata = Mock(BuildClientMetaData)
    def cancellationToken = Mock(BuildCancellationToken)
    def eventConsumer = Mock(BuildEventConsumer)
    def classPath = ClassPath.EMPTY
    BuildSessionState state

    def setup() {
        _ * userHomeServiceRegistry.getServicesFor(_) >> new DefaultServiceRegistry()
        def services = new DefaultServiceRegistry()
        services.add(BuildSessionActionExecutor, actionExecutor)
        services.add(listenerManager)
        _ * crossBuildState.services >> services
        state = new BuildSessionState(userHomeServiceRegistry, crossBuildState, startParameter, buildRequestMetadata, classPath, cancellationToken, clientMetadata, eventConsumer)
    }

    def "does nothing when function does nothing"() {
        def listener = Mock(BuildSessionLifecycleListener)
        def action = Mock(Function)

        given:
        listenerManager.addListener(listener)

        when:
        state.run(action)

        then:
        1 * action.apply(_)
        0 * actionExecutor._
        0 * listener._
    }

    def "fires events before and after build action is run"() {
        def listener = Mock(BuildSessionLifecycleListener)
        def action = Mock(Function)
        def buildAction = Stub(BuildAction)

        given:
        listenerManager.addListener(listener)

        when:
        state.run(action)

        then:
        1 * action.apply(_) >> { BuildSessionContext context -> context.execute(buildAction) }
        1 * listener.afterStart()
        1 * actionExecutor.execute(buildAction, _)
        1 * listener.beforeComplete()
        0 * listener._
    }

    def "cannot run multiple actions against a session"() {
        given:
        state.run {
            it.execute(Stub(BuildAction))
        }

        when:
        state.run {
            it.execute(Stub(BuildAction))
        }

        then:
        thrown(IllegalStateException)
    }
}
