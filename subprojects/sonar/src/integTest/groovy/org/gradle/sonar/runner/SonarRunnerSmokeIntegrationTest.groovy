/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.sonar.runner

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

@Requires(TestPrecondition.JDK7_OR_EARLIER)
@TargetVersions(['default', '2.4'])
@LeaksFileHandles
class SonarRunnerSmokeIntegrationTest extends MultiVersionIntegrationSpec {

    @Rule
    TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider()

    @Rule
    TestResources testResources = new TestResources(temporaryFolder)

    @Rule
    SonarTestServer sonarServer = new SonarTestServer(tempDir, executer)

    List<String> getWarningLogMessages() {
        def warningLogMessages = output.readLines().findAll { it.contains("WARN") }
        warningLogMessages.removeAll { it.contains("'sonar.dynamicAnalysis' is deprecated") }
        warningLogMessages.removeAll { it.contains("H2 database should be used for evaluation purpose only") }
        warningLogMessages
    }

    def "execute 'sonarRunner' task"() {
        given:
        executer.withDeprecationChecksDisabled() // sonar.dynamicAnalysis is deprecated since SonarQube 4.3

        when:
        buildFile << """
           sonarRunner {
              sonarProperties {
                // Use a very long property to test limits
                // https://issues.gradle.org/browse/GRADLE-3168
                property "sonar.foo", ("a" * 200000)
              }
            }
        """

        if (getVersion() != "default") {
            buildFile << """
                sonarRunner {
                  toolVersion = "${getVersion()}"
                }
            """
        }

        run "sonarRunner"

        then:
        sonarServer.assertProjectPresent('org.gradle.test.sonar:SonarTestBuild')

        and: "no unexpected warnings are emitted"
        !warningLogMessages

        and: "no reports directory is created for projects with no production and no test sources"
        !temporaryFolder.file("emptyJavaProject", "build", "test-results").exists()
    }

}
