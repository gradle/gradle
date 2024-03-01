/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.language.fixtures.HelperProcessorFixture
import spock.lang.Issue

class JavaCompileTaskOperationResultIntegTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            include 'processor'
        """
        buildFile << """
            allprojects {
                apply plugin: 'java'
            }
            dependencies {
                compileOnly project(':processor')
                annotationProcessor project(':processor')
            }
        """
        file("src/main/java/SomeClass.java") << """
            @Helper class SomeClass {}
        """
        def processorProjectDir = file("processor")
        def fixture = new HelperProcessorFixture()
        fixture.writeApiTo(processorProjectDir)
        fixture.writeAnnotationProcessorTo(processorProjectDir)
        fixture.writeSupportLibraryTo(processorProjectDir)
    }

    @Issue("https://github.com/gradle/gradle/issues/22999")
    def "listener added during doFirst of JavaCompile can subscribe to task completion events but does not get a callback"() {
        buildFile << """
            import javax.inject.Inject
            import org.gradle.api.services.BuildService
            import org.gradle.api.services.BuildServiceParameters
            import org.gradle.tooling.events.OperationCompletionListener
            import org.gradle.tooling.events.FinishEvent
            import org.gradle.tooling.events.task.java.JavaCompileTaskOperationResult

            apply plugin: JavaCompileModifyingPlugin

            abstract class JavaCompileListener implements BuildService<BuildServiceParameters.None>, OperationCompletionListener {
                void onFinish(FinishEvent event) {
                    println("EVENT: \$event")
                }
            }

            abstract class JavaCompileModifyingPlugin implements Plugin<Project> {
                @Inject
                abstract BuildEventsListenerRegistry getRegistry();

                void apply(Project project) {
                    def listener = project.gradle.sharedServices.registerIfAbsent("listener", JavaCompileListener) { }
                    project.tasks.withType(JavaCompile) { task ->
                        task.doFirst {
                            registry.onTaskCompletion(listener)
                        }
                    }
                }
            }
        """

        when:
        run("compileJava")

        then:
        output.count("EVENT: ") == 0
    }
}
