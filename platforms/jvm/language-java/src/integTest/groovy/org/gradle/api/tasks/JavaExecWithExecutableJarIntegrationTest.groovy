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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import spock.lang.Issue

class JavaExecWithExecutableJarIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("src/main/java/driver/Driver.java") << """
            package driver;

            import java.io.*;

            public class Driver {
                public static void main(String[] args) {
                    try {
                        FileWriter out = new FileWriter("out.txt");
                        for (String arg : args) {
                            out.write(arg);
                        }
                        out.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        """

        buildFile << """
            apply plugin: "java"

            task runWithTask(type: JavaExec) {
                classpath = files(jar)
                args "hello", "world"
            }

            task runWithJavaExec {
                dependsOn jar
                def cp = files(jar)
                doLast {
                    project.javaexec {
                        classpath = cp
                        args "hello", "world"
                    }
                }
            }

            task runWithExecOperations {
                dependsOn jar
                def execOps = services.get(ExecOperations)
                def cp = files(jar)
                doLast {
                    execOps.javaexec {
                        classpath = cp
                        args "hello", "world"
                    }
                }
            }
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/1346")
    @UnsupportedWithConfigurationCache(iterationMatchers = ".* project.javaexec")
    def "can run executable jar with #method"() {

        buildFile << """
            jar {
                manifest {
                    attributes('Main-Class': 'driver.Driver')
                }
            }
        """

        when:
        if (method == 'project.javaexec') {
            executer.expectDocumentedDeprecationWarning("The Project.javaexec(Closure) method has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Use ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action) instead. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_project_exec")
            executer.expectDocumentedDeprecationWarning("Invocation of Task.project at execution time has been deprecated. "+
                "This will fail with an error in Gradle 10.0. " +
                "This API is incompatible with the configuration cache, which will become the only mode supported by Gradle in a future release. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#task_project")
        }
        succeeds taskName

        then:
        file("out.txt").text == """helloworld"""

        where:
        method                    | taskName
        'JavaExec task'           | 'runWithTask'
        'project.javaexec'        | 'runWithJavaExec'
        'ExecOperations.javaexec' | 'runWithExecOperations'
    }

    @Issue("https://github.com/gradle/gradle/issues/1346")
    @UnsupportedWithConfigurationCache(iterationMatchers = ".* project\\.javaexec")
    def "helpful message when jar is not executable with #method"() {

        when:
        if (method == 'project.javaexec') {
            executer.expectDocumentedDeprecationWarning("The Project.javaexec(Closure) method has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Use ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action) instead. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_project_exec")
            executer.expectDocumentedDeprecationWarning("Invocation of Task.project at execution time has been deprecated. "+
                "This will fail with an error in Gradle 10.0. " +
                "This API is incompatible with the configuration cache, which will become the only mode supported by Gradle in a future release. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#task_project")
        }
        fails taskName

        then:
        result.assertHasErrorOutput("no main manifest attribute")

        where:
        method                    | taskName
        'JavaExec task'           | 'runWithTask'
        'project.javaexec'        | 'runWithJavaExec'
        'ExecOperations.javaexec' | 'runWithExecOperations'
    }
}
