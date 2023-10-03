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

package org.gradle.tooling.internal.provider.continuous

import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.deployment.internal.DefaultContinuousExecutionGate
import org.gradle.deployment.internal.Deployment
import org.gradle.deployment.internal.DeploymentInternal
import org.gradle.deployment.internal.DeploymentRegistryInternal
import org.gradle.initialization.BuildRequestMetaData
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.initialization.DefaultBuildRequestContext
import org.gradle.initialization.NoOpBuildEventConsumer
import org.gradle.integtests.fixtures.RedirectStdIn
import org.gradle.internal.buildevents.BuildStartedTime
import org.gradle.internal.buildtree.BuildActionRunner
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.UnitOfWork.InputVisitor
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.logging.text.TestStyledTextOutputFactory
import org.gradle.internal.properties.InputBehavior
import org.gradle.internal.service.scopes.DefaultFileChangeListeners
import org.gradle.internal.service.scopes.DefaultWorkInputListeners
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.session.BuildSessionActionExecutor
import org.gradle.internal.session.BuildSessionContext
import org.gradle.internal.snapshot.CaseSensitivity
import org.gradle.internal.time.Time
import org.gradle.internal.watch.registry.FileWatcherRegistry
import org.gradle.internal.watch.vfs.FileSystemWatchingInformation
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.internal.DisconnectableInputStream
import spock.lang.Timeout
import spock.util.concurrent.PollingConditions

import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

import static org.gradle.internal.properties.InputBehavior.PRIMARY

@RedirectStdIn
class ContinuousBuildActionExecutorTest extends ConcurrentSpec {

    def delegate = Mock(BuildSessionActionExecutor)
    def cancellationToken = new DefaultBuildCancellationToken()
    def buildExecutionTimer = Mock(BuildStartedTime)
    def requestMetadata = Stub(BuildRequestMetaData)
    def requestContext = new DefaultBuildRequestContext(requestMetadata, cancellationToken, new NoOpBuildEventConsumer())
    def startParameter = new StartParameterInternal()
    def action = Stub(BuildAction) {
        getStartParameter() >> startParameter
    }
    def globalListenerManager = new DefaultListenerManager(Scope.Global)
    def userHomeListenerManager = globalListenerManager.createChild(Scopes.UserHome)
    def inputListeners = new DefaultWorkInputListeners(globalListenerManager)
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
    def fileSystemIsWatchingAnyLocations = true
    def fileSystemWatchingInformation = Stub(FileSystemWatchingInformation) {
        isWatchingAnyLocations() >> {
            fileSystemIsWatchingAnyLocations
        }
    }

    def executer = executer()

    private File file = new File('file').absoluteFile
    PollingConditions conditions = new PollingConditions(timeout: 60, initialDelay: 0, factor: 1.25)

    def setup() {
        action.startParameter >> startParameter
    }

    def cleanup() {
        println("Build Output:")
        println(textOutputFactory)
        executorService.shutdownNow()
    }

    def "uses underlying executer when continuous build is not enabled"() {
        given:
        continuousBuildDisabled()

        when:
        executeBuild()

        then:
        1 * delegate.execute(action, buildSessionContext)
        0 * _
    }

    def "allows exceptions to propagate for single builds"() {
        given:
        continuousBuildDisabled()
        1 * delegate.execute(action, buildSessionContext) >> {
            throw new RuntimeException("!")
        }

        when:
        executeBuild()

        then:
        thrown(RuntimeException)
    }

    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    def "close System.in after build"() {
        given:
        continuousBuildDisabled()
        buildIsInteractive()

        when:
        executeBuild()

        then:
        1 * delegate.execute(action, buildSessionContext)
        0 * _
        System.in instanceof DisconnectableInputStream
        System.in.read() == -1
    }

    def "can cancel the build"() {
        given:
        continuousBuildEnabled()
        buildDeclaresInputs()

        when:
        def runningBuild = startBuild()

        then:
        conditions.eventually {
            waitingForChangesMessageAppears()
        }

        when:
        cancellationToken.cancel()

        then:
        buildExits(runningBuild)
        lastLogLine == "Build cancelled."
    }

    def "runs a new build on file change"() {
        given:
        continuousBuildEnabled()
        buildDeclaresInputs()

        when:
        def runningBuild = startBuild()

        then:
        conditions.eventually {
            waitingForChangesMessageAppears()
        }

        when:
        changeListeners.broadcastChange(FileWatcherRegistry.Type.MODIFIED, file.toPath())

        then:
        conditions.eventually {
            rebuiltBecauseOfChange()
        }

        cleanup:
        cancellationToken.cancel()
        buildExits(runningBuild)
    }

    def "only triggers the build when the gatekeeper is open"() {
        given:
        continuousBuildDisabled()
        buildDeclaresInputs()
        buildHasDeployment()

        when:
        def gatekeeper = continuousExecutionGate.createGateKeeper()
        def runningBuild = startBuild()

        then:
        conditions.eventually {
            reloadableDeploymentDetected()
        }
        waitingForChangesMessageAppears()

        when:
        changeListeners.broadcastChange(FileWatcherRegistry.Type.MODIFIED, file.toPath())
        // Wait to make sure the build is not triggered
        Thread.sleep(500)
        then:
        !changeDetected()

        when:
        gatekeeper.open()
        then:
        conditions.eventually {
            rebuiltBecauseOfChange()
        }

        cleanup:
        cancellationToken.cancel()
        buildExits(runningBuild)
    }

    def "triggers build on change when gatekeeper is open"() {
        given:
        continuousBuildDisabled()
        buildDeclaresInputs()
        buildHasDeployment()

        when:
        def gatekeeper = continuousExecutionGate.createGateKeeper()
        def runningBuild = startBuild()

        then:
        conditions.eventually {
            reloadableDeploymentDetected()
        }
        waitingForChangesMessageAppears()

        when:
        def logLengthBeforeOpeneningGatekeeper = logLines.size()
        gatekeeper.open()
        // Wait to make sure the build is not triggered
        Thread.sleep(500)
        then:
        logLines.size() == logLengthBeforeOpeneningGatekeeper

        when:
        changeListeners.broadcastChange(FileWatcherRegistry.Type.MODIFIED, file.toPath())
        then:
        conditions.eventually {
            rebuiltBecauseOfChange()
        }

        cleanup:
        cancellationToken.cancel()
        buildExits(runningBuild)
    }

    def "exits if there are no file system inputs"() {
        given:
        continuousBuildEnabled()

        when:
        executeBuild()
        then:
        lastLogLine == "{failure}Exiting continuous build as Gradle did not detect any file system inputs.{normal}"
    }

    def "exits if Gradle is not watching anything"() {
        given:
        continuousBuildEnabled()
        buildDeclaresInputs()
        fileSystemIsWatchingAnyLocations = false

        when:
        executeBuild()
        then:
        lastLogLine == "{failure}Exiting continuous build as Gradle does not watch any file system locations.{normal}"
    }

    def "does not exit if there was a file events although Gradle is not watching anything anymore"() {
        given:
        continuousBuildEnabled()
        buildDeclaresInputsAndTriggersChange()
        fileSystemIsWatchingAnyLocations = false

        when:
        def runningBuild = startBuild()

        then:
        conditions.eventually {
            rebuiltBecauseOfChange()
        }

        cleanup:
        cancellationToken.cancel()
        buildExits(runningBuild)
    }

    private void buildExits(Future<?> runningBuild) {
        runningBuild.get(1, TimeUnit.SECONDS)
    }

    private Future<?> startBuild() {
        return executorService.submit {
            executeBuild()
        }
    }

    private void buildHasDeployment() {
        deployments.add(Stub(DeploymentInternal))
    }

    private void buildDeclaresInputs() {
        delegate = new BuildSessionActionExecutor() {
            @Override
            BuildActionRunner.Result execute(BuildAction action, BuildSessionContext context) {
                declareInput(file)
                return BuildActionRunner.Result.of(null)
            }
        }
        executer = executer()
    }

    private void buildDeclaresInputsAndTriggersChange() {
        delegate = new BuildSessionActionExecutor() {
            @Override
            BuildActionRunner.Result execute(BuildAction action, BuildSessionContext context) {
                declareInput(file)
                changeListeners.broadcastChange(FileWatcherRegistry.Type.MODIFIED, file.toPath())
                return BuildActionRunner.Result.of(null)
            }
        }
        executer = executer()
    }

    private boolean waitingForChangesMessageAppears() {
        return lastLogLine == "Waiting for changes to input files..."
    }

    private void reloadableDeploymentDetected() {
        assert logLines[-2] == "Reloadable deployment detected. Entering continuous build."
        assert lastLogLine == "Waiting for changes to input files..."
    }

    private void rebuiltBecauseOfChange() {
        assert changeDetected()
        assert waitingForChangesMessageAppears()
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

    private void continuousBuildDisabled() {
        startParameter.continuous = false
    }

    private void buildIsInteractive() {
        requestMetadata.interactive >> true
    }

    private void continuousBuildEnabled() {
        startParameter.continuous = true
    }

    private void executeBuild() {
        executer.execute(action, buildSessionContext)
    }

    private void declareInput(File file) {
        def valueSupplier = Stub(UnitOfWork.InputFileValueSupplier) {
            getFiles() >> TestFiles.fixed(file)
        }
        inputListeners.broadcastFileSystemInputsOf(Stub(UnitOfWork) {
            visitRegularInputs(_ as InputVisitor) >> { InputVisitor visitor ->
                visitor.visitInputFileProperty("test", PRIMARY, valueSupplier)
            }
        }, EnumSet.allOf(InputBehavior))
    }

    private ContinuousBuildActionExecutor executer() {
        new ContinuousBuildActionExecutor(
            inputListeners,
            changeListeners,
            textOutputFactory,
            executorFactory,
            requestContext,
            cancellationToken,
            deploymentRegistry,
            userHomeListenerManager.createChild(Scopes.BuildSession),
            buildExecutionTimer,
            Time.clock(),
            TestFiles.fileSystem(),
            CaseSensitivity.CASE_SENSITIVE,
            fileSystemWatchingInformation,
            delegate)
    }
}
