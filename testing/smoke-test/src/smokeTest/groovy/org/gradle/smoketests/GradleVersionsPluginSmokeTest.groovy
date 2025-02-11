/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber
import org.junit.Assume

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class GradleVersionsPluginSmokeTest extends AbstractPluginValidatingSmokeTest {

    def 'can check for updated versions'() {
        Assume.assumeTrue('Incompatible with Groovy 4: Execution failed for task \':dependencyUpdates\'. > groovy/util/XmlSlurper', VersionNumber.parse(GroovySystem.version).major < 4) // TODO Watch for merge and release of https://github.com/ben-manes/gradle-versions-plugin/pull/656
        given:
        buildFile << """
            plugins {
                id "com.github.ben-manes.versions" version "$TestedVersions.gradleVersions"
            }

            subprojects {
                apply plugin: 'java'

                ${mavenCentralRepository()}
            }
        """
        file("sub1/build.gradle") << """
            dependencies {
                implementation group: 'log4j', name: 'log4j', version: '1.2.14'
            }
        """
        file("sub2/build.gradle") << """
            dependencies {
                implementation group: 'junit', name: 'junit', version: '4.10'
            }
        """
        settingsFile << """
            include "sub1", "sub2"
        """

        when:
        def runner = runner('dependencyUpdates', '-DoutputFormatter=txt')
            // TODO: com.github.benmanes.gradle.versions.updates.DependencyUpdates plugin triggers dependency resolution at execution time
            .withJvmArguments("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

        runner

        // with CC, these are reported as config cache problems only
        runner.expectDeprecationWarningIf(
            GradleContextualExecuter.isNotConfigCache(),
            "Invocation of Task.project at execution time has been deprecated. " +
                "This will fail with an error in Gradle 10.0. " +
                "This API is incompatible with the configuration cache, which will become the only mode supported by Gradle in a future release. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_7.html#task_project",
            "https://github.com/ben-manes/gradle-versions-plugin/issues/910"
        )

        def result = runner.build()

        then:
        result.task(':dependencyUpdates').outcome == SUCCESS
        result.output.contains("- junit:junit [4.10 -> 4.13")
        result.output.contains("- log4j:log4j [1.2.14 -> 1.2.17]")

        file("build/dependencyUpdates/report.txt").exists()
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'com.github.ben-manes.versions': Versions.of(TestedVersions.gradleVersions)
        ]
    }
}
