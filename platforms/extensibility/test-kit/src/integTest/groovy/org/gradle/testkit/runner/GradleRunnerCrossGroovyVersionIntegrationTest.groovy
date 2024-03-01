/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import org.gradle.util.GradleVersion
import org.gradle.util.internal.TextUtil

@Requires([IntegTestPreconditions.NotEmbeddedExecutor, UnitTestPreconditions.Jdk15OrEarlier])
@NonCrossVersion
class GradleRunnerCrossGroovyVersionIntegrationTest extends BaseGradleRunnerIntegrationTest {

    def "current TestKit can run build with old Gradle version that uses Groovy 2"() {
        given:
        buildFile << """
            task testGroovyVersion {
                doLast {
                    assert GroovySystem.version == "2.5.12"
                }
            }
        """

        when:
        def result = runner().withArguments("testGroovyVersion")
            .withGradleVersion("6.8.3")
            .build()

        then:
        result.task(":testGroovyVersion").getOutcome() == TaskOutcome.SUCCESS

        cleanup:
        testKitDaemons(GradleVersion.version("6.8.3")).killAll()
    }

    def "old TestKit can run build with current Gradle"() {
        given:
        def targetingGradle7Test = """
import spock.lang.Specification
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.VersionNumber

class BuildLogicFunctionalTest extends Specification {

    @Rule public TemporaryFolder testProjectDir = new TemporaryFolder();
    File settingsFile
    File buildFile

    def setup() {
        settingsFile = new File(testProjectDir.getRoot(), 'settings.gradle')
        buildFile = new File(testProjectDir.getRoot(), 'build.gradle')
    }

    def "capture groovy version"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << '''
            task testGroovyVersion {
                doLast {
                    assert org.gradle.util.internal.VersionNumber.parse(GroovySystem.version) > org.gradle.util.internal.VersionNumber.parse("3.0")
                }
            }
        '''

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments('testGroovyVersion', '--stacktrace', '--info')
            .withGradleInstallation(new File("${TextUtil.normaliseFileSeparators(buildContext.gradleHomeDir.absolutePath)}"))
            .withDebug($debug)
            .build()

        then:
        result.task(":testGroovyVersion").outcome == TaskOutcome.SUCCESS
    }
}
"""
        buildFile << """
plugins {
    id 'groovy'
}

dependencies {
    testImplementation "org.spockframework:spock-core:1.3-groovy-2.5"
    testImplementation localGroovy()
    testImplementation gradleTestKit()
}

${mavenCentralRepository()}
        """
        file("src/test/groovy/BuildLogicFunctionalTest.groovy") << targetingGradle7Test

        when:
        def result = runner()
            .withArguments("test", '--stacktrace', '--info')
            .withGradleVersion("6.8.3")
            .build()

        then:
        result.task(":test").outcome == TaskOutcome.SUCCESS
    }

}
