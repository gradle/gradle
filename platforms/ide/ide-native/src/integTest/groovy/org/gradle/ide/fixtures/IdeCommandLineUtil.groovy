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

import com.google.common.collect.Maps
import groovy.transform.CompileStatic
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.GUtil

@CompileStatic
abstract class IdeCommandLineUtil {
    private IdeCommandLineUtil() {}

    static String generateGradleProbeInitFile(String ideTaskName, String ideCommandLineTool) {
        return """
            gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS_FULL
            Properties gatherEnvironment() {
                Properties properties = new Properties()
                properties.JAVA_HOME = String.valueOf(System.getenv('JAVA_HOME'))
                properties.GRADLE_USER_HOME = String.valueOf(gradle.gradleUserHomeDir.absolutePath)
                properties.GRADLE_OPTS = String.valueOf(System.getenv('GRADLE_OPTS'))
                return properties
            }

            void assertEquals(key, expected, actual) {
                assert expected[key] == actual[key]
                if (expected[key] != actual[key]) {
                    throw new GradleException(""\"
Environment's \$key did not match!
Expected: \${expected[key]}
Actual: \${actual[key]}
""\")
                }
            }

            rootProject {
                def gradleEnvironment = file("gradle-environment")
                tasks.matching { it.name == '$ideTaskName' }.all { ideTask ->
                    ideTask.doLast {
                        def writer = gradleEnvironment.newOutputStream()
                        gatherEnvironment().store(writer, null)
                        writer.close()
                    }
                }
                gradle.taskGraph.whenReady { taskGraph ->
                    taskGraph.allTasks.last().doLast {
                        if (!gradleEnvironment.exists()) {
                            throw new GradleException("could not determine if $ideCommandLineTool is using the correct environment, did $ideTaskName task run?")
                        } else {
                            def expectedEnvironment = new Properties()
                            expectedEnvironment.load(gradleEnvironment.newInputStream())

                            def actualEnvironment = gatherEnvironment()

                            assertEquals('JAVA_HOME', expectedEnvironment, actualEnvironment)
                            assertEquals('GRADLE_USER_HOME', expectedEnvironment, actualEnvironment)
                            assertEquals('GRADLE_OPTS', expectedEnvironment, actualEnvironment)
                        }
                    }
                }
            }
        """
    }

    static List<String> buildEnvironment(TestFile testDirectory) {
        Map<String, String> envvars = Maps.newHashMap()
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
