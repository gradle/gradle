/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.ide.fixtures

import groovy.transform.CompileStatic
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.GUtil

@CompileStatic
abstract class IdeCommandLineUtil {
    private IdeCommandLineUtil() {}

    static String generateGradleProbeInitFile(String ideTaskName, String ideCommandLineTool) {
        """
            import org.gradle.api.flow.*

            gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS_FULL

            abstract class VerificationPlugin implements Plugin<Project> {
                @Inject
                abstract FlowScope getFlowScope()

                @Inject
                abstract FlowProviders getFlowProviders()

                @Override
                void apply(Project target) {
                    Properties properties = new Properties()
                    properties.JAVA_HOME = String.valueOf(System.getenv('JAVA_HOME'))
                    properties.GRADLE_USER_HOME = String.valueOf(target.gradle.gradleUserHomeDir.absolutePath)
                    properties.GRADLE_OPTS = String.valueOf(System.getenv('GRADLE_OPTS'))

                    target.tap {
                        def gradleEnvironment = file("gradle-environment")
                        tasks.matching { it.name == '$ideTaskName' }.configureEach { ideTask ->
                            ideTask.doLast {
                                try (def writer = gradleEnvironment.newOutputStream()) {
                                    properties.store(writer, null)
                                }
                            }
                        }

                        getFlowScope().always(VerificationFlowAction) { spec ->
                            spec.parameters.gradleEnvironmentPath.set(gradleEnvironment.absolutePath)
                            // Ensure the action runs at the end of the build.
                            spec.parameters.expectedEnvironment.putAll(getFlowProviders().buildWorkResult.map { properties })
                        }
                    }
                }
            }

            abstract class VerificationFlowAction implements FlowAction<VerificationFlowAction.Parameters> {
                interface Parameters extends FlowParameters {
                    @Input Property<String> getGradleEnvironmentPath()
                    @Input MapProperty<Object, Object> getExpectedEnvironment()
                }

                @Override
                void execute(Parameters parameters) {
                    def gradleEnvironment = new File(parameters.gradleEnvironmentPath.get())
                    if (!gradleEnvironment.exists()) {
                        throw new GradleException("could not determine if $ideCommandLineTool is using the correct environment, did $ideTaskName task run?")
                    }

                    def actualEnvironment = new Properties()
                    try (def reader = gradleEnvironment.newInputStream()) {
                        actualEnvironment.load(reader)
                    }

                    def expectedEnvironment = parameters.expectedEnvironment.get()

                    assertEquals('JAVA_HOME', expectedEnvironment, actualEnvironment)
                    assertEquals('GRADLE_USER_HOME', expectedEnvironment, actualEnvironment)
                    assertEquals('GRADLE_OPTS', expectedEnvironment, actualEnvironment)
                }

                static void assertEquals(key, expected, actual) {
                    if (expected[key] != actual[key]) {
                        throw new GradleException(""\"
                            Environment's \$key did not match!
                            Expected: \${expected[key]}
                            Actual: \${actual[key]}
                        ""\".stripIndent(true))
                    }
                }
            }

            rootProject {
                apply plugin: VerificationPlugin
            }
        """
    }

    static List<String> buildEnvironment(TestFile testDirectory) {
        Map<String, String> envvars = new HashMap<>()
        envvars.putAll(System.getenv())

        Properties props = GUtil.loadProperties(testDirectory.file("gradle-environment"))
        assert !props.isEmpty()

        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            if (entry.key == "GRADLE_OPTS") {
                // macOS adds Xdock properties in a funky way that makes us duplicate them on the command-line
                String value = entry.value.toString()
                int lastIndex = value.lastIndexOf("\"-Xdock:name=Gradle\"")
                if (lastIndex > 0) {
                    envvars.put(entry.key.toString(), value.substring(0, lastIndex - 1))
                    continue
                }
            }
            envvars.put(entry.key.toString(), entry.value.toString())
        }

        return envvars.entrySet().collect { "${it.key}=${it.value}".toString() }
    }
}
