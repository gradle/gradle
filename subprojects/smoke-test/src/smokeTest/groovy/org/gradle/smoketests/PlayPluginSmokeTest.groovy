/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class PlayPluginSmokeTest extends AbstractPluginValidatingSmokeTest {

    @Requires(TestPrecondition.JDK11_OR_EARLIER)
    @ToBeFixedForConfigurationCache(because = "unsupported Configuration field")
    def 'build basic Play project'() {
        given:
        useSample("play-example")
        buildFile << """
            plugins {
                id 'org.gradle.playframework' version '${TestedVersions.playframework}'
            }

            repositories {
                ${mavenCentralRepository()}
                ${RepoScriptBlockUtil.lightbendMavenRepositoryDefinition()}
                ${RepoScriptBlockUtil.lightbendIvyRepositoryDefinition()}
            }

            dependencies {
                implementation "com.typesafe.play:play-guice_2.12:2.6.15"
                implementation 'commons-lang:commons-lang:2.6'
                testImplementation "com.google.guava:guava:17.0"
                testImplementation "org.scalatestplus.play:scalatestplus-play_2.12:3.1.2"
                implementation "ch.qos.logback:logback-classic:1.2.3"
            }
        """

        when:
        def result = runner('build')
            .expectLegacyDeprecationWarning(orgGradleUtilTypeDeprecation("VersionNumber", 7))
            .expectLegacyDeprecationWarning(orgGradleUtilTypeDeprecation("CollectionUtils", 7))
            .expectLegacyDeprecationWarning(abstractArchiveTaskArchivePathDeprecation())
            .expectLegacyDeprecationWarning(projectConventionDeprecation())
            .expectLegacyDeprecationWarning(conventionTypeDeprecation())
            .build()

        then:
        result.task(':build').outcome == SUCCESS
    }

    private String orgGradleUtilTypeDeprecation(String type, int major) {
        return "The org.gradle.util.$type type has been deprecated. " +
            "This is scheduled to be removed in Gradle 9.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_${major}.html#org_gradle_util_reports_deprecations"
    }

    private String abstractArchiveTaskArchivePathDeprecation() {
        return "The AbstractArchiveTask.archivePath property has been deprecated." +
                " This is scheduled to be removed in Gradle 9.0." +
                " Please use the archiveFile property instead." +
                " See https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.tasks.bundling.AbstractArchiveTask.html#org.gradle.api.tasks.bundling.AbstractArchiveTask:archivePath for more details.";
    }

    private String projectConventionDeprecation() {
        return "The Project.getConvention() method has been deprecated. " +
            "This is scheduled to be removed in Gradle 9.0. " +
            "Consult the upgrading guide for further information: " +
            "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#deprecated_access_to_conventions"
    }

    private String conventionTypeDeprecation() {
        return "The org.gradle.api.plugins.Convention type has been deprecated. " +
            "This is scheduled to be removed in Gradle 9.0. " +
            "Consult the upgrading guide for further information: " +
            "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#deprecated_access_to_conventions"
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'org.gradle.playframework': Versions.of(TestedVersions.playframework)
        ]
    }
}
