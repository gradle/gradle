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

import org.gradle.api.execution.internal.DefaultTaskInputsListeners
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.deployment.internal.Deployment
import org.gradle.deployment.internal.DeploymentInternal
import org.gradle.deployment.internal.DeploymentRegistryInternal
import org.gradle.initialization.BuildRequestMetaData
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.initialization.DefaultBuildRequestContext
import org.gradle.initialization.DefaultContinuousExecutionGate
import org.gradle.initialization.NoOpBuildEventConsumer
import org.gradle.integtests.fixtures.RedirectStdIn
import org.gradle.internal.buildevents.BuildStartedTime
import org.gradle.internal.buildtree.BuildActionRunner
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.logging.text.TestStyledTextOutputFactory
import org.gradle.internal.service.scopes.DefaultFileChangeListeners
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.session.BuildSessionActionExecutor
import org.gradle.internal.session.BuildSessionContext
import org.gradle.internal.snapshot.CaseSensitivity
import org.gradle.internal.time.Time
import org.gradle.internal.watch.registry.FileWatcherRegistry
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.internal.DisconnectableInputStream
import spock.lang.Timeout
import spock.util.concurrent.PollingConditions

import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@RedirectStdIn
class ContinuousBuildActionExecutorTest extends ConcurrentSpec {

    def delegate = Mock(BuildSessionActionExecutor)
    def action = Mock(BuildAction)
    def cancellationToken = new DefaultBuildCancellationToken()
    def buildExecutionTimer = Mock(BuildStartedTime)
    def requestMetadata = Stub(BuildRequestMetaData)
    def requestContext = new DefaultBuildRequestContext(requestMetadata, cancellationToken, new NoOpBuildEventConsumer())
    def startParameter = new StartParameterInternal()
    def globalListenerManager = new DefaultListenerManager(Scope.Global)
    def userHomeListenerManager = globalListenerManager.createChild(Scopes.UserHome)
    def inputsListeners = new DefaultTaskInputsListeners(globalListenerManager)
    def changeListeners = new DefaultFileChangeListeners(userHomeListenerManager)
    List<Deployment> deployments = []
    def continuousExecutionGate = new DefaultContinuousExecutionGate()
    def deploymentRegistry = Stub(DeploymentRegistryInternal) {
        runningDeployments >> deployments
        executionGate >> continuousExecutionGate
    }
    def buildSessionContext = Mock(BuildSessionContext)
    def textOutputFactory = new TestStyledTextOutputFactory()
    def executorService = Executors.newCachedThreadPool()

    def executer = executer()
    Future<?> runningBuild

    private File file = new File('file').absoluteFile
    PollingConditions conditions = new PollingConditions(timeout: 60, initialDelay: 0, factor: 1.25)

    def setup() {
        action.startParameter >> startParameter
    }

    def cleanup() {
        executorService.shutdownNow()
    }

    def "uses underlying executer when continuous build is not enabled"() {
        when:
        singleBuild()
        executeBuild()

        then:
        1 * delegate.execute(action, buildSessionContext)
    }

    def "allows exceptions to propagate for single builds"() {
        when:
        singleBuild()
        1 * delegate.execute(action, buildSessionContext) >> {
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
        1 * delegate.execute(action, buildSessionContext)
        System.in instanceof DisconnectableInputStream
        System.in.read() == -1
    }

    def "can cancel the build"() {
        when:
        continuousBuildWithInputs()
        runningBuild = startBuild()

        then:
        conditions.eventually {
            lastLogLine == "Waiting for changes to input files of tasks..."
        }

        when:
        cancellationToken.cancel()

        then:
        buildExits()
        lastLogLine == "Build cancelled."
    }

    def "runs a new build on file change"() {
        when:
        continuousBuildWithInputs()
        startBuild()

        then:
        conditions.eventually {
            waitingForChanges()
        }

        when:
        changeListeners.broadcastChange(FileWatcherRegistry.Type.MODIFIED, file.toPath())

        then:
        conditions.eventually {
            rebuiltBecauseOfChange()
        }

        when:
        cancellationToken.cancel()
        then:
        buildExits()
    }

    def "only triggers the build when the gatekeeper is open"() {
        when:
        buildWithDeployment()
        def gatekeeper = continuousExecutionGate.createGateKeeper()
        startBuild()

        then:
        conditions.eventually {
            reloadableDeploymentDetected()
        }

        when:
        changeListeners.broadcastChange(FileWatcherRegistry.Type.MODIFIED, file.toPath())
        // Wait to make sure the build is not triggered
        Thread.sleep(500)
        then:
        waitingForChanges()
        !changeDetected()

        when:
        gatekeeper.open()
        then:
        conditions.eventually {
            rebuiltBecauseOfChange()
        }

        when:
        cancellationToken.cancel()
        then:
        buildExits()

        cleanup:
        println(textOutputFactory)
    }

    def "exits if there are no file system inputs"() {
        when:
        continuousBuild()
        1 * delegate.execute(action, buildSessionContext)
        then:
        executeBuild()
    }

    private void buildExits() {
        runningBuild.get(1, TimeUnit.SECONDS)
    }

    private Future<?> startBuild() {
        runningBuild = executorService.submit {
            executeBuild()
        }
        return runningBuild
    }

    private void continuousBuildWithInputs() {
        buildWithInputs()
        continuousBuild()
    }

    private void buildWithDeployment() {
        buildWithInputs()
        deployments.add(Stub(DeploymentInternal))
    }

    private void buildWithInputs() {
        delegate = new BuildSessionActionExecutor() {
            @Override
            BuildActionRunner.Result execute(BuildAction action, BuildSessionContext context) {
                declareInput(file)
                return BuildActionRunner.Result.of(null)
            }
        }
        executer = executer()
    }

    private boolean waitingForChanges() {
        return lastLogLine == "Waiting for changes to input files of tasks..."
    }

    private void reloadableDeploymentDetected() {
        assert logLines[-2] == "Reloadable deployment detected. Entering continuous build."
        assert lastLogLine == "Waiting for changes to input files of tasks..."
    }

    private void rebuiltBecauseOfChange() {
        assert changeDetected()
        assert waitingForChanges()
    }

    private boolean changeDetected() {
        logLines[-2] == "Change detected, executing build..." ||
            lastLogLine == "Change detected, executing build..."
    }

    private String getLastLogLine() {
        return logLines.last()
    }

    private List<String> getLogLines() {
        return textOutputFactory.toString().readLines().findAll { !it.empty }
    }

    private void singleBuild() {
        startParameter.continuous = false
    }

    private void interactiveBuild() {
        requestMetadata.interactive >> true
    }

    private void continuousBuild() {
        startParameter.continuous = true
    }

    private void executeBuild() {
        executer.execute(action, buildSessionContext)
    }

    private void declareInput(File file) {
        inputsListeners.broadcastFileSystemInputsOf(Mock(TaskInternal), TestFiles.fixed(file))
    }

    private ContinuousBuildActionExecutor executer() {
        new ContinuousBuildActionExecutor(inputsListeners, changeListeners, textOutputFactory, executorFactory, requestContext, cancellationToken, deploymentRegistry, userHomeListenerManager.createChild(Scopes.BuildSession), buildExecutionTimer, Time.clock(), TestFiles.fileSystem(), CaseSensitivity.CASE_SENSITIVE, delegate)
    }
}
