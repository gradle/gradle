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

    def "can debug with org.gradle.debug=true"() {
        given:
        debugPortIsFree()

        when:
        def gradle = executer.withArgument("-Dorg.gradle.debug=true").withTasks("help").start()

        then:
        ConcurrentTestUtil.poll() {
            // Connect, resume threads, and disconnect from VM
            jdwpClient.connect().dispose()
        }
        gradle.waitForFinish()
    }

    boolean debugPortIsFree() {
        ConcurrentTestUtil.poll(30) {
            boolean listening = false
            Socket probe;
            try {
                probe = new Socket(InetAddress.getLocalHost(), 5005)
                // something is listening, keep polling
                listening = true
            } catch (Exception e) {
                // nothing listening - exit the polling loop
            } finally {
                if (probe != null) {
                    probe.close()
                }
            }

            if (listening) {
                throw new IllegalStateException("Something is listening on port 5005")
            }
        }
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
                }
            }
        """
        args("--no-scan")
        then:
        succeeds("assertBuildScanRequest")
    }

    def "running gradle with --scan without plugin applied results in error message"() {
        when:
        buildFile << """
            task someTask
        """
        then:
        fails("someTask", "--scan")
        and:
        errorOutput.contains("Build scan cannot be requested as build scan plugin is not applied.\n"
            + "For more information, please visit: https://gradle.com/get-started")
    }

    def "running gradle with --scan sets `scan` system property if not yet set"() {
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
        succeeds("assertBuildScanSysProperty", "-Dscan=true", "--scan")
        fails("assertBuildScanSysProperty", "-Dscan=false", "--scan")
    }

    def "running gradle with --no-scan sets `scan` system property to false"() {
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
        succeeds("assertBuildScanSysProperty", "-Dscan=true", "--no-scan")
    }

    def "cannot combine --scan and --no-scan"() {
        given:
        requireGradleDistribution()
        withDummyBuildScanPlugin()
        when:
        args("--scan", "--no-scan")

        fails("tasks")
        then:
        errorOutput.contains("Commandline switches '--scan' and '--no-scan' are mutual exclusive and can not be combined.")
    }

    def withDummyBuildScanPlugin() {
        buildFile << """
        class DummyBuildScanPlugin implements Plugin<Project> {
            void apply(Project project){
            }
        }
        apply plugin:DummyBuildScanPlugin
        """
    }
}
