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
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.jvm.JDWPUtil
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Unroll

class JavaExecDebugIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    JDWPUtil debugClient = new JDWPUtil()

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = ".* :runProjectJavaExec")
    def "debug is disabled by default with task :#taskName"() {
        setup:
        sampleProject """
            debugOptions {
            }
        """

        expect:
        succeeds(taskName)

        where:
        taskName << ['runJavaExec', 'runProjectJavaExec', 'runExecOperationsJavaExec', 'test']
    }

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = ".* :runProjectJavaExec")
    def "debug session fails without debugger with task :#taskName"() {
        setup:
        sampleProject """
            debugOptions {
                enabled = true
                server = false
            }
        """

        expect:
        def failure = executer.withTasks(taskName).withStackTraceChecksDisabled().runWithFailure()
        failure.assertHasErrorOutput('ERROR: transport error 202: connect failed:')

        where:
        taskName << ['runJavaExec', 'runProjectJavaExec', 'runExecOperationsJavaExec', 'test']
    }

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = ".* :runProjectJavaExec")
    def "can debug Java exec with socket listen type debugger (server = false) with task :#taskName"() {
        setup:
        sampleProject """
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
        taskName << ['runJavaExec', 'runProjectJavaExec', 'runExecOperationsJavaExec', 'test']
    }

    @Ignore
    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = ".* :runProjectJavaExec")
    def "can debug Java exec with socket attach type debugger (server = true) with task :#taskName"() {
        setup:
        sampleProject """
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
        taskName << ['runJavaExec', 'runProjectJavaExec', 'runExecOperationsJavaExec', 'test']
    }

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = ".* :runProjectJavaExec")
    def "debug options overrides debug property with task :#taskName"() {
        setup:
        sampleProject """
            debug = true

            debugOptions {
                enabled = false
                server = false
            }
        """

        expect:
        succeeds(taskName)

        where:
        taskName << ['runJavaExec', 'runProjectJavaExec', 'runExecOperationsJavaExec', 'test']
    }

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = ".* :runProjectJavaExec")
    def "if custom debug argument is passed to the build then debug options is ignored with task :#taskName"() {
        setup:
        sampleProject """
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
        taskName << ['runJavaExec', 'runProjectJavaExec', 'runExecOperationsJavaExec', 'test']
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
                 testImplementation 'junit:junit:4.13'
            }

            task runJavaExec(type: JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = "driver.Driver"

                $javaExecConfig
            }

            task runProjectJavaExec {
                doLast {
                    project.javaexec {
                        classpath = sourceSets.main.runtimeClasspath
                        mainClass = "driver.Driver"

                        $javaExecConfig
                    }
                }
                dependsOn sourceSets.main.runtimeClasspath
            }

            task runExecOperationsJavaExec {
                def runClasspath = sourceSets.main.runtimeClasspath
                dependsOn runClasspath
                def execOps = services.get(ExecOperations)
                doLast {
                    execOps.javaexec {
                        classpath = runClasspath
                        mainClass = "driver.Driver"

                        $javaExecConfig
                    }
                }
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
