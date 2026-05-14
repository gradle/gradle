/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.testkit.runner

import org.gradle.test.precondition.Requires
import org.gradle.testkit.runner.enduser.BaseTestKitEndUserIntegrationTest

import static org.gradle.test.preconditions.TestExecutionPreconditions.NotEmbeddedExecutor

/**
 * Verifies that an embedded TestKit build ({@code withDebug(true)}) sees the
 * instrumentation agent active, and that the
 * {@code org.gradle.internal.instrumentation.agent} property is ignored on
 * this path. See {@code DefaultConnection.getServices()} for the rationale:
 * in embedded mode no daemon is launched, so the property (which only gates
 * daemon-launch wiring) cannot influence whether the agent is on the JVM.
 */
@Requires(value = NotEmbeddedExecutor, reason = NOT_EMBEDDED_REASON)
class TestKitInstrumentationIntegrationTest extends BaseTestKitEndUserIntegrationTest {

    def "embedded TestKit build enables instrumentation agent and ignores the agent property when it is #description"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'java-gradle-plugin'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useSpock()
                    }
                }
            }

            gradlePlugin {
                plugins {
                    register('hello') {
                        id = 'org.example.hello'
                        implementationClass = 'org.example.HelloPlugin'
                    }
                }
            }
        """

        file("src/main/java/org/example/HelloPlugin.java") << """
            package org.example;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            public class HelloPlugin implements Plugin<Project> {
                public void apply(Project project) {}
            }
        """

        file("src/test/groovy/AgentTest.groovy") << """
            import org.gradle.testkit.runner.GradleRunner
            import spock.lang.Specification
            import java.nio.file.Files

            class AgentTest extends Specification {
                def "agent is active"() {
                    given:
                    def projectDir = Files.createTempDirectory("inner").toFile()
                    new File(projectDir, "settings.gradle").text = "rootProject.name = 'inner'"
                    new File(projectDir, "build.gradle").text = '''
                        import org.gradle.internal.instrumentation.agent.AgentStatus
                        tasks.register("checkAgent") {
                            doLast {
                                def applied = services.get(AgentStatus).isAgentInstrumentationEnabled()
                                println "agent applied = " + applied
                            }
                        }
                    '''

                    when:
                    def result = GradleRunner.create()
                        .withProjectDir(projectDir)
                        .withArguments($args)
                        .withDebug(true)
                        .forwardOutput()
                        .build()

                    then:
                    result.output.contains("agent applied = true")
                }
            }
        """

        expect:
        succeeds('test')

        where:
        description    | args
        "absent"       | '"checkAgent"'
        "set to true"  | '"checkAgent", "-Dorg.gradle.internal.instrumentation.agent=true"'
        "set to false" | '"checkAgent", "-Dorg.gradle.internal.instrumentation.agent=false"'
    }
}
