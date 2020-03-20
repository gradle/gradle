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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import spock.lang.Issue

class JavaExecWithExecutableJarIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("src/main/java/driver/Driver.java") <<"""
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
                doLast {
                    project.javaexec {
                        classpath = files(jar)
                        args "hello", "world"
                    }
                }
            }
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/1346")
    @ToBeFixedForInstantExecution
    def "can run JavaExec with an executable jar"() {

        buildFile << """
            jar {
                manifest {
                    attributes('Main-Class': 'driver.Driver')
                }
            }
        """

        when:
        succeeds "runWithTask"

        then:
        file("out.txt").text == """helloworld"""
    }

    @Issue("https://github.com/gradle/gradle/issues/1346")
    @ToBeFixedForInstantExecution(because = "Task.getProject() during execution")
    def "can run javaexec with executable jar"() {

        buildFile << """
            jar {
                manifest {
                    attributes('Main-Class': 'driver.Driver')
                }
            }
        """

        when:
        succeeds "runWithJavaExec"

        then:
        file("out.txt").text == """helloworld"""
    }

    @ToBeFixedForInstantExecution(because = "Task.getProject() during execution")
    def "can run JavaExec with an executable jar configured in the application plugin"() {

        buildFile << """
            apply plugin: 'application'
            application {
                mainClass.set('driver.Driver')
            }
        """

        when:
        succeeds "runWithTask"

        then:
        file("out.txt").text == """helloworld"""
    }

    @ToBeFixedForInstantExecution
    def "can run javaexec with executable jar configured in the application plugin"() {

        buildFile << """
            apply plugin: 'application'
            application {
                mainClass.set('driver.Driver')
            }
        """

        when:
        succeeds "runWithJavaExec"

        then:
        file("out.txt").text == """helloworld"""
    }

    @Issue("https://github.com/gradle/gradle/issues/1346")
    @ToBeFixedForInstantExecution
    def "helpful message when jar is not executable"() {

        when:
        fails "runWithTask"

        then:
        result.assertHasErrorOutput("no main manifest attribute")
    }
}
