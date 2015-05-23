/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.exec

import org.gradle.api.JavaVersion
import org.gradle.api.execution.internal.TaskInputsListener
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.initialization.BuildRequestMetaData
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.initialization.DefaultBuildRequestContext
import org.gradle.initialization.NoOpBuildEventConsumer
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.filewatch.FileSystemChangeWaiter
import org.gradle.internal.invocation.BuildAction
import org.gradle.logging.TestStyledTextOutputFactory
import org.gradle.util.Clock
import spock.lang.AutoCleanup
import spock.lang.Specification

class ContinuousModeBuildActionExecuterTest extends Specification {
    def delegate = Mock(BuildActionExecuter)
    def action = Mock(BuildAction)
    def cancellationToken = new DefaultBuildCancellationToken()
    def clock = Mock(Clock)
    def requestMetadata = Stub(BuildRequestMetaData)
    def requestContext = new DefaultBuildRequestContext(requestMetadata, cancellationToken, new NoOpBuildEventConsumer())
    def actionParameters = Stub(BuildActionParameters)
    def waiter = Mock(FileSystemChangeWaiter)
    def listenerManager = new DefaultListenerManager()
    @AutoCleanup("stop")
    def executorFactory = new DefaultExecutorFactory()
    def executer = executer()

    private File file = new File('file')

    def setup() {
        requestMetadata.getBuildTimeClock() >> clock
    }

    def "uses underlying executer when continuous building is not enabled"() {
        when:
        singleBuild()
        executeBuild()

        then:
        1 * delegate.execute(action, requestContext, actionParameters)
        0 * waiter._
    }

    def "allows exceptions to propagate for single builds"() {
        when:
        singleBuild()
        1 * delegate.execute(action, requestContext, actionParameters) >> {
            throw new RuntimeException("!")
        }
        executeBuild()

        then:
        thrown(RuntimeException)
    }

    def "waits for trigger in continuous mode when build works"() {
        when:
        continuousBuilding()
        1 * delegate.execute(action, requestContext, actionParameters) >> {
            declareInput(file)
        }
        executeBuild()

        then:
        1 * waiter.wait(_, _, _) >> {
            cancellationToken.cancel()
        }
    }

    def "exits if there are no file system inputs"() {
        when:
        continuousBuilding()
        1 * delegate.execute(action, requestContext, actionParameters)
        executeBuild()

        then:
        0 * waiter.wait(_, _, _)
    }

    def "waits for trigger in continuous mode when build fails"() {
        when:
        continuousBuilding()
        1 * delegate.execute(action, requestContext, actionParameters) >> {
            declareInput(file)
            throw new Exception("!")
        }
        executeBuild()

        then:
        1 * waiter.wait(_, _, _) >> {
            cancellationToken.cancel()
        }
    }

    def "keeps running after failures in continuous mode"() {
        when:
        continuousBuilding()
        executeBuild()

        then:
        1 * delegate.execute(action, requestContext, actionParameters) >> {
            declareInput(file)
        }

        and:
        1 * waiter.wait(_, _, _)

        and:
        1 * delegate.execute(action, requestContext, actionParameters) >> {
            declareInput(file)
            throw new Exception("!")
        }

        and:
        1 * waiter.wait(_, _, _) >> {
            cancellationToken.cancel()
        }
    }

    def "doesn't prevent use on java 6 when not using continuous"() {
        given:
        executer = executer(JavaVersion.VERSION_1_6)

        when:
        singleBuild()

        and:
        executeBuild()

        then:
        noExceptionThrown()
    }

    def "prevents use on java 6 when using continuous"() {
        given:
        executer = executer(JavaVersion.VERSION_1_6)

        when:
        continuousBuilding()

        and:
        executeBuild()

        then:
        def e = thrown IllegalStateException
        e.message == "Continuous building requires Java 1.7 or later."
    }

    def "can use on all versions later than 7"() {
        given:
        executer = executer(javaVersion)

        when:
        continuousBuilding()

        and:
        executeBuild()

        then:
        noExceptionThrown()

        where:
        javaVersion << JavaVersion.values().findAll { it >= JavaVersion.VERSION_1_7 }
    }

    private void singleBuild() {
        actionParameters.continuousModeEnabled >> false
    }

    private void continuousBuilding() {
        actionParameters.continuousModeEnabled >> true
    }

    private void executeBuild() {
        executer.execute(action, requestContext, actionParameters)
    }

    private void declareInput(File file) {
        listenerManager.getBroadcaster(TaskInputsListener).onExecute(Mock(TaskInternal), new SimpleFileCollection(file))
    }

    private ContinuousModeBuildActionExecuter executer(JavaVersion javaVersion = JavaVersion.VERSION_1_7) {
        new ContinuousModeBuildActionExecuter(delegate, listenerManager, new TestStyledTextOutputFactory(), javaVersion, executorFactory, waiter)
    }

}
