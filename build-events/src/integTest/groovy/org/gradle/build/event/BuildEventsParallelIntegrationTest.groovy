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
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent
import org.junit.Rule

class BuildEventsParallelIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()
    }

    def "event handling does not block other work"() {
        buildFile << """
            import ${BuildEventsListenerRegistry.name}
            import ${OperationCompletionListener.name}
            import ${FinishEvent.name}
            import ${TaskFinishEvent.name}
            import ${BuildServiceParameters.name}

            interface Params extends BuildServiceParameters {
                Property<String> getPrefix()
            }

            abstract class BlockingListener implements OperationCompletionListener, BuildService<Params> {
                @Override
                void onFinish(FinishEvent event) {
                    if (event instanceof TaskFinishEvent) {
                        ${server.callFromBuildUsingExpression("parameters.prefix.get() + event.descriptor.taskPath")}
                    } else {
                        throw new IllegalArgumentException()
                    }
                }
            }
            
            def registry = project.services.get(BuildEventsListenerRegistry)
            registry.onTaskCompletion(gradle.sharedServices.registerIfAbsent('listener1', BlockingListener) {
                parameters.prefix = "handle1_"
            })
            registry.onTaskCompletion(gradle.sharedServices.registerIfAbsent('listener2', BlockingListener) {
                parameters.prefix = "handle2_"
            })

            task a { }
            task b { 
                dependsOn a
                doFirst {
                    ${server.callFromBuild("run_:b")}
                }
            } 
        """

        server.expectConcurrent("run_:b", "handle1_:a", "handle2_:a")
        server.expectConcurrent("handle1_:b", "handle2_:b")

        when:
        run("b")

        then:
        noExceptionThrown()
    }
}
