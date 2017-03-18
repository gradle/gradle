/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.launcher.debug.JDWPUtil
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Unroll

class CommandLineIntegrationSpec extends AbstractIntegrationSpec {
    @Rule JDWPUtil jdwpClient = new JDWPUtil(5005)

    @IgnoreIf({ GradleContextualExecuter.parallel })
    @Unroll
    def "reasonable failure message when --max-workers=#value"() {
        given:
        requireGradleDistribution() // otherwise exception gets thrown in testing infrastructure

        when:
        args("--max-workers=$value")

        then:
        fails "help"

        and:
        errorOutput.trim().readLines()[0] == "Argument value '$value' given for --max-workers option is invalid (must be a positive, non-zero, integer)"

        where:
        value << ["-1", "0", "foo", " 1"]
    }

    @Unroll
    def "reasonable failure message when org.gradle.workers.max=#value"() {
        given:
        requireGradleDistribution() // otherwise exception gets thrown in testing infrastructure

        when:
        args("-Dorg.gradle.workers.max=$value")

        then:
        fails "help"

        and:
        failure.assertHasDescription "Value '$value' given for org.gradle.workers.max system property is invalid (must be a positive, non-zero, integer)"

        where:
        value << ["-1", "0", "foo", " 1"]
    }

    @IgnoreIf({ !CommandLineIntegrationSpec.debugPortIsFree() })
    def "can debug with org.gradle.debug=true"() {
        when:
        def gradle = executer.withArgument("-Dorg.gradle.debug=true").withTasks("help").start()

        then:
        ConcurrentTestUtil.poll() {
            // Connect, resume threads, and disconnect from VM
            jdwpClient.connect().dispose()
        }
        gradle.waitForFinish()
    }

    static boolean debugPortIsFree() {
        boolean free = true

        ConcurrentTestUtil.poll(30) {
            Socket probe
            try {
                probe = new Socket(InetAddress.getLocalHost(), 5005)
                // something is listening, keep polling
                free = false
            } catch (Exception e) {
                // nothing listening - exit the polling loop
            } finally {
                probe?.close()
            }
        }

        free
    }

    def "running gradle with --scan flag marks BuildScanRequest as requested"() {
        when:
        withDummyBuildScanPlugin()
        buildFile << """
            task assertBuildScanRequest {
                doLast {
                    assert project.services.get(org.gradle.internal.scan.BuildScanRequest).collectRequested() == true
                    assert project.services.get(org.gradle.internal.scan.BuildScanRequest).collectDisabled() == false
                }
            }
        """
        args("--scan")

        then:
        succeeds("assertBuildScanRequest")
    }

    def "running gradle with --no-scan flag marks BuildScanRequest as disabled"() {
        when:
        withDummyBuildScanPlugin()
        buildFile << """
            task assertBuildScanRequest {
                doLast {
                    assert project.services.get(org.gradle.internal.scan.BuildScanRequest).collectDisabled() == true
                    assert project.services.get(org.gradle.internal.scan.BuildScanRequest).collectRequested() == false
                }
            }
        """
        args("--no-scan")

        then:
        succeeds("assertBuildScanRequest")
    }

    def "running gradle with --scan without plugin applied results in a warning"() {
        when:
        buildFile << """
            task someTask
        """
        then:
        succeeds("someTask", "--scan")

        and:
        outputContains("Build scan cannot be created since the build scan plugin has not been applied.\n"
            + "For more information on how to apply the build scan plugin, please visit https://gradle.com/scans/help/gradle-cli.")
    }

    def "running gradle with --scan sets `scan` system property to true if not yet set"() {
        when:
        withDummyBuildScanPlugin()
        buildFile << """
            task assertBuildScanSysProperty {
                doLast {
                    assert Boolean.getBoolean('scan')
                }
            }
        """

        then:
        succeeds("assertBuildScanSysProperty", "--scan")
        fails("assertBuildScanSysProperty", "--scan", "-Dscan=false")
    }

    def "running gradle with --no-scan sets `scan` system property to false if not yet set"() {
        when:
        withDummyBuildScanPlugin()
        buildFile << """
            task assertBuildScanSysProperty {
                doLast {
                    assert System.getProperty('scan') == 'false'
                }
            }
        """

        then:
        succeeds("assertBuildScanSysProperty", "--no-scan")
        fails("assertBuildScanSysProperty", "--no-scan", "-Dscan=true")
    }

    def "running gradle with --no-scan without build scan plugin applied results in no error"() {
        when:
        buildFile.text = ""
        then:
        succeeds("help", "--no-scan")
    }

    def "cannot combine --scan and --no-scan"() {
        given:
        requireGradleDistribution()
        withDummyBuildScanPlugin()

        when:
        args("--scan", "--no-scan")

        then:
        fails("tasks")
        errorOutput.contains("Command line switches '--scan' and '--no-scan' are mutually exclusive and must not be used together.")
    }

    def withDummyBuildScanPlugin() {
        file("buildSrc/src/main/groovy/BuildScanPlugin.groovy").text = """
            package com.gradle.test.build.dummy
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            
            class BuildScanPlugin implements Plugin<Project> {
                void apply(Project project){
                }
            }
        """
        buildFile << """
        apply plugin:com.gradle.test.build.dummy.BuildScanPlugin
        """
    }
}
