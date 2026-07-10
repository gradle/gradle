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

package org.gradle.integtests.tooling.r35

import org.gradle.integtests.tooling.fixture.AbstractHttpCrossVersionSpec
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.tooling.ProjectConnection

class BuildProgressCrossVersionSpec extends AbstractHttpCrossVersionSpec {

    def "generate events for task actions"() {
        given:
        settingsFile << "rootProject.name = 'single'"
        buildFile << 'apply plugin:"java"'
        file("src/main/java/Thing.java") << """class Thing { }"""

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .forTasks('compileJava')
                    .addProgressListener(events)
                    .run()
        }

        then:
        events.assertIsABuild()

        and:
        def compileJavaActions = events.operations.findAll { it.descriptor.displayName.matches('Execute .*( action [0-9]+/[0-9]+)? for :compileJava') }
        compileJavaActions.size() > 0
        compileJavaActions[0].hasAncestor { it.descriptor.displayName == 'Task :compileJava' }
    }

    @TargetGradleVersion('>=4.0 <5.1')
    def "generates events for worker actions (pre 5.1)"() {
        expect:
        runBuildWithWorkerRunnable() != null
    }

    @TargetGradleVersion('>=4.0 <7.0')
    def "generates events for worker actions with old Worker API (post 5.1)"() {
        expect:
        runBuildWithWorkerRunnable() != null
    }

    @TargetGradleVersion('>=5.6')
    def "generates events for worker actions with new Worker API (post 5.1)"() {
        expect:
        runBuildWithWorkerAction() != null
    }

    private ProgressEvents.Operation runBuildWithWorkerAction() {
        buildFile << """
            import org.gradle.workers.*
            abstract class MyWorkerAction implements WorkAction<WorkParameters.None>{
                @Override public void execute() {
                    // Do nothing
                }
            }
            task runInWorker {
                doLast {
                    def workerExecutor = services.get(WorkerExecutor)
                    workerExecutor.noIsolation().submit(MyWorkerAction) { }
                }
            }
        """

        runBuildInAction()
    }

    private ProgressEvents.Operation runBuildWithWorkerRunnable() {
        buildFile << """
            import org.gradle.workers.*
            class TestRunnable implements Runnable {
                @Override public void run() {
                    // Do nothing
                }
            }
            task runInWorker {
                doLast {
                    def workerExecutor = services.get(WorkerExecutor)
                    workerExecutor.submit(TestRunnable) { config ->
                        config.displayName = 'MyWorkerAction'
                    }
                }
            }
        """

        runBuildInAction()
    }

    private ProgressEvents.Operation runBuildInAction() {
        settingsFile << "rootProject.name = 'single'"

        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .forTasks('runInWorker')
                    .addProgressListener(events)
                    .run()
        }

        events.assertIsABuild()
        events.operation('Task :runInWorker').descendant('MyWorkerAction')
    }
}
