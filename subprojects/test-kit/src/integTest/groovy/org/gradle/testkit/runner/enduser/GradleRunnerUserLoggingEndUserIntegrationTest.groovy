/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testkit.runner.enduser

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

@IgnoreIf({ GradleContextualExecuter.embedded }) // These tests run builds that themselves run a build in a test worker with 'gradleTestKit()' dependency, which needs to pick up Gradle modules from a real distribution
class GradleRunnerUserLoggingEndUserIntegrationTest extends BaseTestKitEndUserIntegrationTest {

    @NotYetImplemented
    def "can use user slfj logging in tests using testkit"() {
        when:
        buildFile << """
            ${mavenCentralRepository()}

            apply plugin: "groovy"

            dependencies {
                testImplementation gradleTestKit()
                testImplementation 'org.slf4j:slf4j-simple:1.7.21'
                testImplementation('org.spockframework:spock-core:1.0-groovy-2.4') {
                    exclude module: 'groovy-all'
                }
            }

            test.testLogging.showStandardStreams = true
        """

        and:
        file("src/test/groovy/Test.groovy") << """
            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import org.junit.Rule
            import org.junit.rules.TemporaryFolder
            import spock.lang.Specification
            import org.slf4j.LoggerFactory

            class Test extends Specification {

                @Rule TemporaryFolder testProjectDir = new TemporaryFolder()

                def "execute helloWorld task"() {
                    given:
                    testProjectDir.newFile('build.gradle') << ''
                    LoggerFactory.getLogger(getClass()).error("custom error output")

                    when:
                    def result = GradleRunner.create()
                        .withProjectDir(testProjectDir.root)
                        .withArguments('help')
                        .withDebug($debug)
                        .build()

                    then:
                    noExceptionThrown()
                }
            }
        """

        then:
        succeeds 'build'
        !output.contains("SLF4J: Class path contains multiple SLF4J bindings.")
        output.contains("custom error output")
    }

}
