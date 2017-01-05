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

package org.gradle.testkit.runner

import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.testkit.runner.enduser.BaseTestKitEndUserIntegrationTest
import org.gradle.util.GradleVersion

class GradleRunnerPreNoSourceTaskOutcomeIntegrationTest extends BaseTestKitEndUserIntegrationTest {

    def setup() {
        // NO-SOURCE was introduced in 3.4
        version(new ReleasedVersionDistributions().getDistribution(GradleVersion.version("3.3")))
        buildScript """
            apply plugin: 'groovy'
            dependencies {
                compile localGroovy()
                testCompile('org.spockframework:spock-core:1.0-groovy-2.4') {
                    exclude module: 'groovy-all'
                    testCompile gradleTestKit()
                }
            }

            repositories {
                jcenter()
            }
        """
    }

    def "testkit version with no NO-SOURCE support detects NO-SOURCE outcome in gradle versions as SKIPPED"() {
        when:
        file("src/test/groovy/Test.groovy").text = """
        import org.gradle.testkit.runner.GradleRunner
        import static org.gradle.testkit.runner.TaskOutcome.*
        import org.junit.Rule
        import org.junit.rules.TemporaryFolder
        import spock.lang.Specification

        class Test extends Specification {
            @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
            File buildFile

            def setup() {
                buildFile = testProjectDir.newFile('build.gradle')
            }

            def "execute task with no sources"() {
                given:
                buildFile << '''
                        task task1(type:Copy) {
                            from("notexist")
                            into("notexisteither")
                        }
                    '''

                when:
                def result = GradleRunner.create()
                    .withProjectDir(testProjectDir.root)
                    .withArguments('task1')
                    .withGradleDistribution(new URI('${distribution.getBinDistribution().toURI()}'))
                    .build()
                then:
                result.task(':task1').outcome == SKIPPED
            }
        }
        """

        then:
        succeeds 'test'
    }

    GradleExecuter version(GradleDistribution dist) {
        dist.executer(temporaryFolder, IntegrationTestBuildContext.INSTANCE)
            .expectDeprecationWarning()
            .inDirectory(testDirectory)
    }
}
