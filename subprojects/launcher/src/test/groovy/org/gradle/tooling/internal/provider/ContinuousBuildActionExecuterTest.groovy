/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.execution.internal.DefaultTaskInputsListener
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.deployment.internal.DeploymentRegistryInternal
import org.gradle.initialization.BuildRequestMetaData
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.initialization.DefaultBuildRequestContext
import org.gradle.initialization.NoOpBuildEventConsumer
import org.gradle.internal.buildevents.BuildStartedTime
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.filewatch.FileSystemChangeWaiter
import org.gradle.internal.filewatch.FileSystemChangeWaiterFactory
import org.gradle.internal.filewatch.PendingChangesListener
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.logging.text.TestStyledTextOutputFactory
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.time.Clock
import org.gradle.internal.time.Time
import org.gradle.launcher.exec.BuildActionExecuter
import org.gradle.launcher.exec.BuildActionParameters
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.DisconnectableInputStream
import org.gradle.util.RedirectStdIn
import org.junit.Rule
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

class ContinuousBuildActionExecuterTest extends ConcurrentSpec {

    @Rule
    RedirectStdIn redirectStdIn = new RedirectStdIn()

    def delegate = Mock(BuildActionExecuter)
    def action = Mock(BuildAction)
    def cancellationToken = new DefaultBuildCancellationToken()
    def buildExecutionTimer = Mock(BuildStartedTime)
    def requestMetadata = Stub(BuildRequestMetaData)
    def requestContext = new DefaultBuildRequestContext(requestMetadata, cancellationToken, new NoOpBuildEventConsumer())
    def actionParameters = Stub(BuildActionParameters)
    def waiterFactory = Mock(FileSystemChangeWaiterFactory)
    def waiter = Mock(FileSystemChangeWaiter)
    def inputsListener = new DefaultTaskInputsListener()
    def buildSessionScopeServices = Stub(ServiceRegistry)
    def listenerManager = Stub(ListenerManager)
    def pendingChangesListener = Mock(PendingChangesListener)
    def deploymentRegistry = Mock(DeploymentRegistryInternal)
    def executer = executer()

    private File file = new File('file')

    def setup() {
        buildSessionScopeServices.get(ListenerManager) >> listenerManager
        buildSessionScopeServices.get(BuildStartedTime) >> buildExecutionTimer
        buildSessionScopeServices.get(Clock) >> Time.clock()
        listenerManager.getBroadcaster(PendingChangesListener) >> pendingChangesListener
        waiterFactory.createChangeWaiter(_, _, _) >> waiter
        buildSessionScopeServices.get(DeploymentRegistryInternal) >> deploymentRegistry
        waiter.isWatching() >> true
    }

    def "uses underlying executer when continuous build is not enabled"() {
        when:
        singleBuild()
        executeBuild()

        then:
        1 * delegate.execute(action, requestContext, actionParameters, _)
        1 * deploymentRegistry.runningDeployments >> []
        0 * waiterFactory._
    }

    def "allows exceptions to propagate for single builds"() {
        when:
        singleBuild()
        1 * delegate.execute(action, requestContext, actionParameters, _) >> {
            throw new RuntimeException("!")
        }
        executeBuild()

        then:
        thrown(RuntimeException)
    }

    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    def "close System.in after build"() {
        when:
        singleBuild()
        interactiveBuild()
        executeBuild()

        then:
        1 * delegate.execute(action, requestContext, actionParameters, _)
        1 * deploymentRegistry.runningDeployments >> []
        0 * waiterFactory._
        System.in instanceof DisconnectableInputStream
        System.in.read() == -1
    }

    def "waits for waiter"() {
        when:
        continuousBuild()
        1 * delegate.execute(action, requestContext, actionParameters, _) >> {
            declareInput(file)
        }
        executeBuild()

        then:
        1 * waiter.wait(_, _) >> {
            cancellationToken.cancel()
        }
    }

    def "exits if there are no file system inputs"() {
        when:
        continuousBuild()
        1 * delegate.execute(action, requestContext, actionParameters, _)
        executeBuild()

        then:
        waiter.isWatching() >> false
        0 * waiter.wait(_, _)
    }

    private void singleBuild() {
        actionParameters.continuous >> false
    }

    private void interactiveBuild() {
        requestMetadata.interactive >> true
    }

    private void continuousBuild() {
        actionParameters.continuous >> true
    }

    private void executeBuild() {
        executer.execute(action, requestContext, actionParameters, buildSessionScopeServices)
    }

    private void declareInput(File file) {
        inputsListener.onExecute(Mock(TaskInternal), TestFiles.fixed(file))
    }

    private ContinuousBuildActionExecuter executer() {
        new ContinuousBuildActionExecuter(delegate, waiterFactory, inputsListener, new TestStyledTextOutputFactory(), executorFactory)
    }
}
