/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.tooling.r54

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.plugins.ide.eclipse.model.EclipseModel

@TargetGradleVersion(">=5.4")
class RunEclipseSynchronizationTasksCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        file("sub1").mkdirs()

        buildFile << """
            apply plugin: 'eclipse'

            task foo {
            }

            task bar {
            }

            project(":sub") {
                apply plugin: 'eclipse'

                task bar {
                }
            }
        """
        settingsFile << "include 'sub'"
    }

    def "can run tasks upon Eclipse synchronization"() {
        setup:
        buildFile << "eclipse { synchronizationTasks 'foo' }"

        def projectsLoadedHandler = new IntermediateResultHandlerCollector<Void>()
        def buildFinishedHandler = new IntermediateResultHandlerCollector<EclipseModel>()
        def out = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            connection.action().projectsLoaded(new TellGradleToRunSyncTasks(), projectsLoadedHandler)
                .buildFinished(new LoadEclipseModel(), buildFinishedHandler)
                .build()
                .setStandardOutput(out)
                .forTasks()
                .run()
        }

        then:
        taskExecuted(out, ":foo")
    }

    def "can use task reference in sync task list"() {
        setup:
        buildFile << "eclipse { synchronizationTasks foo }"

        def projectsLoadedHandler = new IntermediateResultHandlerCollector<Void>()
        def buildFinishedHandler = new IntermediateResultHandlerCollector<EclipseModel>()
        def out = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            connection.action().projectsLoaded(new TellGradleToRunSyncTasks(), projectsLoadedHandler)
                .buildFinished(new LoadEclipseModel(), buildFinishedHandler)
                .build()
                .setStandardOutput(out)
                .forTasks()
                .run()
        }

        then:
        taskExecuted(out, ":foo")
    }

    def "task paths are resolved correctly"() {
        setup:
        buildFile << """
            project(':sub') {
                eclipse {
                    synchronizationTasks 'bar'
                }
            }
        """

        def projectsLoadedHandler = new IntermediateResultHandlerCollector<Void>()
        def buildFinishedHandler = new IntermediateResultHandlerCollector<EclipseModel>()
        def out = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            connection.action().projectsLoaded(new TellGradleToRunSyncTasks(), projectsLoadedHandler)
                .buildFinished(new LoadEclipseModel(), buildFinishedHandler)
                .build()
                .setStandardOutput(out)
                .forTasks()
                .run()
        }

        then:
        taskExecuted(out, ":sub:bar")
    }


    def "execute placeholder task when no task is configured for synchronization"() {
        setup:
        def projectsLoadedHandler = new IntermediateResultHandlerCollector<Void>()
        def buildFinishedHandler = new IntermediateResultHandlerCollector<EclipseModel>()
        def out = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            connection.action().projectsLoaded(new TellGradleToRunSyncTasks(), projectsLoadedHandler)
                .buildFinished(new LoadEclipseModel(), buildFinishedHandler)
                .build()
                .setStandardOutput(out)
                .forTasks()
                .run()
        }

        then:
        taskExecuted(out, ":nothing")
    }

    def "does not override client-specified tasks"() {
        setup:
        buildFile << "eclipse { synchronizationTasks bar }"

        def projectsLoadedHandler = new IntermediateResultHandlerCollector<Void>()
        def buildFinishedHandler = new IntermediateResultHandlerCollector<EclipseModel>()
        def out = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            connection.action().projectsLoaded(new TellGradleToRunSyncTasks(), projectsLoadedHandler)
                .buildFinished(new LoadEclipseModel(), buildFinishedHandler)
                .build()
                .setStandardOutput(out)
                .forTasks("foo")
                .run()
        }

        then:
        taskExecuted(out, ":foo")
        taskExecuted(out, ":bar")
    }


    def "placeholder task never overlaps with project task"() {
        setup:
        buildFile << """
            task nothing {
            }

            task nothing_ {
            }
        """

        def projectsLoadedHandler = new IntermediateResultHandlerCollector<Void>()
        def buildFinishedHandler = new IntermediateResultHandlerCollector<EclipseModel>()
        def out = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            connection.action().projectsLoaded(new TellGradleToRunSyncTasks(), projectsLoadedHandler)
                .buildFinished(new LoadEclipseModel(), buildFinishedHandler)
                .build()
                .setStandardOutput(out)
                .forTasks()
                .run()
        }

        then:
        !taskExecuted(out, ":nothing")
        !taskExecuted(out, ":nothing_")
        taskExecuted(out, ":nothing__")
    }

    private def taskExecuted(ByteArrayOutputStream out, String taskPath) {
        out.toString().contains("> Task $taskPath ")
    }

}
