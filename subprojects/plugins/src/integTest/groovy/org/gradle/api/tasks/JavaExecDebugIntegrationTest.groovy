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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.jvm.JDWPUtil
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.junit.Rule
import spock.lang.Ignore

class JavaExecDebugIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    JDWPUtil debugClient = new JDWPUtil()

    @ToBeFixedForInstantExecution
    def "debug is disabled by default"(String taskName) {
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

    @ToBeFixedForInstantExecution
    def "debug session fails without debugger"(String taskName) {
        setup:
        sampleProject"""
            debugOptions {
                enabled = true
                server = false
            }
        """

        expect:
        def failure = executer.withTasks(taskName).withStackTraceChecksDisabled().runWithFailure()
        failure.assertHasErrorOutput('ERROR: transport error 202: connect failed: Connection refused')

        where:
        taskName << ['runJavaExec', 'runProjectJavaExec', 'test']
    }

    @ToBeFixedForInstantExecution
    def "can debug Java exec with socket listen type debugger (server = false)"(String taskName) {
        setup:
        sampleProject"""    
            debugOptions {
                enabled = true
                server = false
                suspend = false
                port = $debugClient.port
            }
        """
        debugClient.listen()

        expect:
        succeeds(taskName)

        where:
        taskName << ['runJavaExec', 'runProjectJavaExec', 'test']
    }

    @Ignore
    def "can debug Java exec with socket attach type debugger (server = true)"(String taskName) {
        setup:
        sampleProject"""    
            debugOptions {
                enabled = true
                server = true
                suspend = true
                port = $debugClient.port
            }
        """

        when:
        def handle = executer.withTasks(taskName).start()
        ConcurrentTestUtil.poll(60) {
            assert handle.standardOutput.contains('Listening for transport dt_socket at address')
        }

        then:
        debugClient.connect().dispose()

        then:
        handle.waitForFinish()

        where:
        taskName << ['runJavaExec', 'runProjectJavaExec', 'test']
    }

    @ToBeFixedForInstantExecution
    def "debug options overrides debug property"(String taskName) {
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

    @ToBeFixedForInstantExecution
    def "if custom debug argument is passed to the build then debug options is ignored"(String taskName) {
        setup:
        sampleProject"""    
            debugOptions {
                enabled = true
                server = false
                suspend = false
            }
            
            jvmArgs "-agentlib:jdwp=transport=dt_socket,server=n,suspend=n,address=$debugClient.port"
        """

        debugClient.listen()

        expect:
        succeeds(taskName)
        output.contains "Debug configuration ignored in favor of the supplied JVM arguments: [-agentlib:jdwp=transport=dt_socket,server=n,suspend=n,address=$debugClient.port]"

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
                onOutput { descriptor, event ->
                    logger.lifecycle("Test: " + descriptor + " produced standard out/err: " + event.message )
                }

                $javaExecConfig
            }
        """
    }
}
