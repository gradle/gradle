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

package org.gradle.smoketests

import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber
import spock.lang.Issue

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
class ShadowPluginSmokeTest extends AbstractPluginValidatingSmokeTest {

    @Issue('https://plugins.gradle.org/plugin/com.github.johnrengelman.shadow')
    @UnsupportedWithConfigurationCache(iterationMatchers = ["shadow plugin [45].*", "shadow plugin 6\\.0.*"])
    def 'shadow plugin #version'() {
        given:
        buildFile << """
            import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

            plugins {
                id 'java' // or 'groovy' Must be explicitly applied
                id 'com.github.johnrengelman.shadow' version '$version'
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation 'commons-collections:commons-collections:3.2.2'
            }

            shadowJar {
                transform(ServiceFileTransformer)

                manifest {
                    attributes 'Test-Entry': 'PASSED'
                }
            }
            """.stripIndent()

        when:
        def result = runner('shadowJar').expectDeprecationWarningIf(
                VersionNumber.parse(version) <= VersionNumber.parse("6.0.0"),
                "The AbstractArchiveTask.archivePath property has been deprecated. " +
                        "This is scheduled to be removed in Gradle 9.0. " +
                        "Please use the archiveFile property instead. " +
                        "See https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.tasks.bundling.AbstractArchiveTask.html#org.gradle.api.tasks.bundling.AbstractArchiveTask:archivePath for more details.",
                ""
        ).build()

        then:
        result.task(':shadowJar').outcome == SUCCESS
        assertConfigurationCacheStateStored()

        when:
        runner('clean').build()
        result = runner('shadowJar').expectDeprecationWarningIf(
                VersionNumber.parse(version) <= VersionNumber.parse("6.0.0"),
                "The AbstractArchiveTask.archivePath property has been deprecated. " +
                        "This is scheduled to be removed in Gradle 9.0. " +
                        "Please use the archiveFile property instead. " +
                        "See https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.tasks.bundling.AbstractArchiveTask.html#org.gradle.api.tasks.bundling.AbstractArchiveTask:archivePath for more details.",
                ""
        ).build()

        then:
        result.task(':shadowJar').outcome == SUCCESS
        assertConfigurationCacheStateLoaded()

        where:
        version << TestedVersions.shadow
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'com.github.johnrengelman.shadow': TestedVersions.shadow
        ]
    }
}
