/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.gradle.test.fixtures.file.TestFile


class JavaAgentFixture {
    void writeProjectTo(TestFile projectDir) {
        projectDir.file("settings.gradle") << """
            include 'javaagent'
        """
        def javaAgentProjectDir = projectDir.createDir('javaagent')
        javaAgentProjectDir.createDir('src/main/java')
        javaAgentProjectDir.file('src/main/java/Agent.java').text = javaAgentClassContent
        javaAgentProjectDir.file('build.gradle').text = buildScriptContent
    }

    String getJavaAgentClassContent() {
        return """
            import java.lang.instrument.Instrumentation;

            public class Agent {
                public static void premain(String args, Instrumentation instrumentation){
                    ${agentAction}
                }
            }
        """
    }

    String getAgentAction() {
        return """System.out.println("JavaAgent configured!");"""
    }

    String getBuildScriptContent() {
        return """
            plugins {
                id 'java'
            }

            jar {
                manifest {
                    attributes(
                            'Premain-Class': 'Agent',
                            'Implementation-Title': "JavaAgent",
                            'Implementation-Version': project.version
                    )
                }
            }
        """
    }

    String useJavaAgent(String forkOptions) {
        """
            ${commandLineArgumentProviderClassContent}

            configurations {
                javaagent
            }

            dependencies {
                javaagent project(':javaagent')
            }

            ${configureForkOptions(forkOptions)}
        """
    }

    private String getCommandLineArgumentProviderClassContent() {
        return """
            abstract class JavaAgentCommandLineArgumentProvider implements CommandLineArgumentProvider {
                @Classpath
                abstract ConfigurableFileCollection getJarFile()

                @Override
                List<String> asArguments() {
                    File agentJarFile = jarFile.singleFile
                    ["-javaagent:\${agentJarFile.absolutePath}".toString()]
                }
            }
        """
    }

    private String configureForkOptions(String forkOptions) {
        """
            def javaAgent = objects.newInstance(JavaAgentCommandLineArgumentProvider)
            javaAgent.jarFile.from(configurations.javaagent)
            ${forkOptions}.jvmArgumentProviders.add(javaAgent)
        """
    }
}
