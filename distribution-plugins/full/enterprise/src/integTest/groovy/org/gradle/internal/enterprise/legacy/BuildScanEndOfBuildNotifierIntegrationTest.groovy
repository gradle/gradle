/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.enterprise.legacy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.internal.scan.eob.BuildScanEndOfBuildNotifier
import spock.lang.Issue

@UnsupportedWithConfigurationCache(because = "legacy plugin is incompatible")
class BuildScanEndOfBuildNotifierIntegrationTest extends AbstractIntegrationSpec {

    def scanPlugin = new GradleEnterprisePluginLegacyContactPointFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        settingsFile << scanPlugin.pluginManagement()

        scanPlugin.with {
            logConfig = true
            logApplied = true
            publishDummyPlugin(executer)
        }

        buildFile << """
            def notifier = services.get(${BuildScanEndOfBuildNotifier.name})
        """
    }

    def "can observe successful build after completion of user logic and build outcome is reported"() {
        when:
        buildFile << """
            notifier.notify {
                println "failure is null: \${it.failure == null}"
            }
            // user logic registered _after_ listener registered
            gradle.projectsEvaluated {
                println "projects evaluated"
            }
        """

        run()

        then:
        output.contains("projects evaluated")
        output.matches("""(?s).*
BUILD SUCCESSFUL in [ \\dms]+
1 actionable task: 1 executed.*
failure is null: true
.*""")
    }

    def "can observe failed build after completion of user logic and build outcome is reported"() {
        when:
        buildFile << """
            task t { doFirst { throw new Exception("!") } }
            notifier.notify {
                println "failure message: \${it.failure.cause.message}"
                System.err.println "notified"
            }
            // user logic registered _after_ listener registered
            gradle.projectsEvaluated {
                println "projects evaluated"
                System.err.println "projects evaluated"
            }
        """

        runAndFail("t")

        then:
        outputContains("projects evaluated")
        output.matches("""(?s).*
1 actionable task: 1 executed.*
failure message: Execution failed for task ':t'.
.*""")

        errorOutput.contains("projects evaluated")
        result.error.matches("""(?s)projects evaluated

FAILURE: Build failed with an exception\\..*
BUILD FAILED in [ \\dms]+
notified
\$""")
    }

    @Issue("https://github.com/gradle/gradle/issues/7511")
    @UnsupportedWithConfigurationCache
    def "can observe failed build after failure in included build buildFinished action"() {
        when:
        settingsFile << """
            includeBuild("child")
        """
        buildFile << """
            notifier.notify {
                println "failure message: \${it.failure.cause.message}"
            }
            task t {
                dependsOn gradle.includedBuild("child").task(":t")
                doLast { }
            }
        """
        file("child/build.gradle") << """
            gradle.buildFinished {
                throw new RuntimeException("broken")
            }
            task t
        """

        runAndFail("t")

        then:
        output.matches("""(?s).*
1 actionable task: 1 executed.*
failure message: broken
.*""")
    }

    def "can only register one listener"() {
        when:
        buildFile << """
            notifier.notify { }
            notifier.notify { }
        """

        def failure = runAndFail()

        then:
        failure.assertHasCause("listener already set to")
    }
}
