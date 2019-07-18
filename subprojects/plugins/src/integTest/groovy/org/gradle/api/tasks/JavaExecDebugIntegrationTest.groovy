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

package org.gradle.api.tasks


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.jvm.JDWPUtil
import org.junit.Rule

class JavaExecDebugIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    JDWPUtil jdwpClient = new JDWPUtil()

    def "Debug is disabled by default"(String taskName) {
        setup:
        sampleProject"""
            debugOptions {
            }
        """

        expect:
        succeeds(taskName)

        where:
        taskName << ['runJavaExec', 'runProjectJavaExec', 'test']
    }

    def "Debug mode can be disabled"(String taskName) {
        setup:
        sampleProject"""
            debugOptions {
                enabled = false
            }
        """

        expect:
        succeeds(taskName)

        where:
        taskName << ['runJavaExec', 'runProjectJavaExec', 'test']
    }

    def "Debug session fails without debugger"(String taskName) {
        setup:
        sampleProject"""
            debugOptions {
                enabled = true
                server = false
            }
        """

        expect:
        def failure = fails(taskName)
        failure.assertHasErrorOutput('ERROR: transport error 202: connect failed: Connection refused')

        where:
        taskName << ['runJavaExec', 'runProjectJavaExec', 'test']
    }

    def "Can debug Java exec"(String taskName) {
        setup:
        sampleProject"""    
            debugOptions {
                enabled = true
                server = false
                suspend = false
                port = $jdwpClient.port
            }
        """
        jdwpClient.listen()

        expect:
        succeeds(taskName)

        where:
        taskName << ['runJavaExec', 'runProjectJavaExec', 'test']
    }

    def "Debug options overrides debug property"(String taskName) {
        setup:
        sampleProject"""    
            debug = true
        
            debugOptions {
                enabled = false
                server = false
            }
        """

        expect:
        succeeds(taskName)

        where:
        taskName << ['runJavaExec', 'runProjectJavaExec', 'test']
    }

    def "If custom debug argument is passed to the build then debug options is ignored"(String taskName) {
        setup:
        sampleProject"""    
            debugOptions {
                enabled = true
                server = false
                suspend = false
            }
            
            jvmArgs "-agentlib:jdwp=transport=dt_socket,server=n,suspend=n,address=$jdwpClient.port"
        """

        jdwpClient.listen()

        expect:
        succeeds(taskName)
        output.contains "Debug configuration ignored in favor of the supplied JVM arguments: [-agentlib:jdwp=transport=dt_socket,server=n,suspend=n,address=$jdwpClient.port]"

        where:
        taskName << ['runJavaExec', 'runProjectJavaExec', 'test']
    }

    private def sampleProject(String javaExecConfig) {
        file("src/main/java/driver/Driver.java").text = """
            package driver;

            public class Driver {
                public static void main(String[] args) {
                    System.exit(0);
                }
            }
            
        """

        file('src/test/java/driver/DriverTest.java').text = """
            package driver;

            public class DriverTest {
                 @org.junit.Test public void driverTest() {
                    org.junit.Assert.assertTrue(true);
                 }
            }
        """

        buildFile.text = """
            plugins {
                id 'java-library'
            }

            repositories {
                 ${mavenCentralRepository()}
            }

            dependencies {
                 testImplementation 'junit:junit:4.12'
            }

            task runJavaExec(type: JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                main "driver.Driver"
                
                $javaExecConfig
            }

            task runProjectJavaExec {
                doLast {
                    project.javaexec {
                        classpath = sourceSets.main.runtimeClasspath
                        main "driver.Driver"
                        
                        $javaExecConfig
                    }
                }
                dependsOn sourceSets.main.runtimeClasspath
            }

            tasks.withType(Test) {
                $javaExecConfig
            }
        """
    }
}
