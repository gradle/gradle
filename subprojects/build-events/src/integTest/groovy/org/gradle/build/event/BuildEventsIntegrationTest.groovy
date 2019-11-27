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

package org.gradle.build.event

import org.gradle.api.services.BuildServiceParameters
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskStartEvent


class BuildEventsIntegrationTest extends AbstractIntegrationSpec {
    def "listener can subscribe to task events"() {
        buildFile << """
            import ${ProgressListenerRegistry.name}
            import ${ProgressListener.name}
            import ${ProgressEvent.name}
            import ${TaskStartEvent.name}
            import ${TaskFinishEvent.name}
            import javax.inject.Inject
            import ${BuildServiceParameters.name}

            abstract class MyListener implements ProgressListener, BuildService<BuildServiceParameters.None> {
                @Override
                void statusChanged(ProgressEvent event) {
                    if (event instanceof TaskStartEvent) {
                        println("EVENT: start \${event.descriptor.taskPath}")
                    } else if (event instanceof TaskFinishEvent) {
                        println("EVENT: finish \${event.descriptor.taskPath}")
                    }
                }
            }

            abstract class MyPlugin implements Plugin<Project> {
                @Inject
                abstract ProgressListenerRegistry getListenerRegistry()

                void apply(Project project) {
                    def listener = project.gradle.sharedServices.registerIfAbsent("listener", MyListener) { }
                    listenerRegistry.register(listener)
                }
            }

            apply plugin: MyPlugin

            task a {}
            task b {}
            task c { dependsOn a, b }
        """

        when:
        run("a")

        then:
        output.count("EVENT:") == 2
        outputContains("start :a")
        outputContains("finish :a")

        when:
        run("a")

        then:
        output.count("EVENT:") == 2
        outputContains("start :a")
        outputContains("finish :a")
    }
}
