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

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.util.GradleVersion
import spock.lang.IgnoreIf

@IgnoreIf({ GradleContextualExecuter.embedded })
class GradleRunnerCrossGroovyVersionIntegrationTest extends BaseGradleRunnerIntegrationTest {

    def "can run simple build using groovy2"(String testedGradleVersion, Closure<GradleRunner> gradleVersionConfigurator) {
        given:
        buildFile << """
            task writeGroovyVersion {
                doLast {
                    file("version.txt").with {
                        createNewFile()
                        text = GroovySystem.version
                    }
                }
            }
        """

        when:
        def someRunner = runner().withArguments("writeGroovyVersion")
        gradleVersionConfigurator.call(someRunner)
        someRunner.build()

        then:
        file("version.txt").text == groovyVersion

        cleanup:
        testKitDaemons(GradleVersion.version(testedGradleVersion)).killAll()

        where:
        testedGradleVersion               | groovyVersion         | gradleVersionConfigurator
        "6.8.3"                           | "2.5.12"              | { it.withGradleVersion("6.8.3") }
        GradleVersion.current().version   | GroovySystem.version  | { it.withGradleInstallation(buildContext.gradleHomeDir) }
    }

    def "groovy2-based gradle can run build via testkit using a groovy3-based gradle"() {
        given:
        def targetingGradle7Test = """
import spock.lang.Specification
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion

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
            task writeGroovyVersion {
                doLast {
                    file("version.txt").with {
                        createNewFile()
                        text = GroovySystem.version
                    }
                }
            }
        '''

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments('writeGroovyVersion', '--stacktrace', '--info')
            .withGradleInstallation(new File("${buildContext.gradleHomeDir}"))
            .withDebug($debug)
            .build()

        then:
        GradleVersion.current().version == "6.8.3"
        new File(testProjectDir.getRoot(), "version.txt").text == "3.0.7"
        result.task(":writeGroovyVersion").outcome == TaskOutcome.SUCCESS
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

repositories {
    mavenCentral()
}
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
