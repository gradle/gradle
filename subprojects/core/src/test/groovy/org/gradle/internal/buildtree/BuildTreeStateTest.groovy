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

package org.gradle.internal.buildtree


import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.id.UniqueId
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.work.ProjectParallelExecutionController
import spock.lang.Specification

import java.util.function.Function

class BuildTreeStateTest extends Specification {
    def listenerManager = new DefaultListenerManager(Scopes.BuildSession)
    def actionExecutor = Mock(BuildTreeActionExecutor)
    def buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate())
    BuildTreeState state

    def setup() {
        def services = new DefaultServiceRegistry()
        services.add(Stub(BuildOperationProgressEventEmitter))
        services.add(Mock(BuildModelParameters))
        services.add(Mock(ProjectParallelExecutionController))
        services.add(BuildTreeActionExecutor, actionExecutor)
        services.add(listenerManager)
        state = new BuildTreeState(buildInvocationScopeId, services, Stub(BuildTreeModelControllerServices.Supplier))
    }

    def "does nothing when function does nothing"() {
        def listener = Mock(BuildTreeLifecycleListener)
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
        def listener = Mock(BuildTreeLifecycleListener)
        def action = Mock(Function)
        def buildAction = Stub(BuildAction)

        given:
        listenerManager.addListener(listener)

        when:
        state.run(action)

        then:
        1 * action.apply(_) >> { BuildTreeContext context -> context.execute(buildAction) }
        1 * listener.afterStart()
        1 * actionExecutor.execute(buildAction, _)
        1 * listener.beforeStop()
        0 * listener._
    }

    def "cannot run multiple actions against a tree"() {
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
