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

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class PlayPluginSmokeTest extends AbstractPluginValidatingSmokeTest {

    @Requires(UnitTestPreconditions.Jdk11OrEarlier)
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
            .expectLegacyDeprecationWarning(BaseDeprecations.ABSTRACT_ARCHIVE_TASK_ARCHIVE_PATH_DEPRECATION)
            .expectLegacyDeprecationWarning(BaseDeprecations.CONVENTION_TYPE_DEPRECATION)
            .expectLegacyDeprecationWarning(BaseDeprecations.JAVA_PLUGIN_CONVENTION_DEPRECATION)
            .expectLegacyDeprecationWarning(COPY_PROCESSING_SPEC_SET_FILE_MODE_DEPRECATION)
            .expectLegacyDeprecationWarning(orgGradleUtilTypeDeprecation("VersionNumber", 8))
            .build()

        then:
        result.task(':build').outcome == SUCCESS
    }

    private String orgGradleUtilTypeDeprecation(String type, int major) {
        "The org.gradle.util.$type type has been deprecated. " +
            "This is scheduled to be removed in Gradle 9.0. " +
            "Consult the upgrading guide for further information: ${new DocumentationRegistry().getDocumentationFor("upgrading_version_${major}", "org_gradle_util_reports_deprecations${major >= 8 ? '_8' : ''}")}"
    }

    public static final String COPY_PROCESSING_SPEC_SET_FILE_MODE_DEPRECATION = "The CopyProcessingSpec.setFileMode(Integer) method has been deprecated. " +
        "This is scheduled to be removed in Gradle 9.0. " +
        "Please use the filePermissions(Action) method instead. " +
        "Consult the upgrading guide for further information: " +
        new DocumentationRegistry().getDocumentationFor("upgrading_version_8","unix_file_permissions_deprecated")


    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'org.gradle.playframework': Versions.of(TestedVersions.playframework)
        ]
    }
}
