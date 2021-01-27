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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.util.GradleVersion
import org.gradle.util.VersionNumber
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.internal.reflect.TypeValidationContext.Severity.WARNING

class AsciidoctorPluginSmokeTest extends AbstractPluginValidatingSmokeTest {

    final VersionNumber version3 = VersionNumber.parse("3.0.0")

    @Issue('https://github.com/asciidoctor/asciidoctor-gradle-plugin/releases')
    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def 'asciidoctor legacy plugin'() {
        given:
        buildFile << """
            buildscript {
                ${jcenterRepository()}
                dependencies {
                    classpath "org.asciidoctor:asciidoctor-gradle-plugin:1.5.11"
                }
            }

            apply plugin: 'org.asciidoctor.gradle.asciidoctor'
            """.stripIndent()

        file('src/docs/asciidoc/test.adoc') << """
            = Line Break Doc Title
            :hardbreaks:

            Rubies are red,
            Topazes are blue.
            """.stripIndent()

        when:
        def result = runner('asciidoc').build()

        then:
        file('build/asciidoc').isDirectory()

        expectDeprecationWarnings(result,
            "Type 'AsciidoctorTask': non-property method 'asGemPath()' should not be annotated with: @Optional, @InputDirectory. " +
                "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. " +
                "Execution optimizations are disabled due to the failed validation. " +
                "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.",
            "Property 'logDocuments' has redundant getters: 'getLogDocuments()' and 'isLogDocuments()'. " +
                "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. " +
                "Execution optimizations are disabled due to the failed validation. " +
                "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.",
            "Property 'separateOutputDirs' has redundant getters: 'getSeparateOutputDirs()' and 'isSeparateOutputDirs()'. " +
                "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. " +
                "Execution optimizations are disabled due to the failed validation. " +
                "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.",
        )
    }

    @Issue('https://github.com/asciidoctor/asciidoctor-gradle-plugin/releases')
    @Unroll
    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def 'asciidoctor plugin #version'() {
        given:
        buildFile << """
            plugins {
                id '${pluginIdForVersion(version)}' version '${version}'
            }

            repositories {
                ${jcenterRepository()}
            }
        """

        file('src/docs/asciidoc/test.adoc') << """
            = Line Break Doc Title
            :hardbreaks:

            Rubies are red,
            Topazes are blue.
            """.stripIndent()

        when:
        def result = runner('asciidoc').build()

        then:
        if (VersionNumber.parse(version) >= version3) {
            file('build/docs/asciidoc').isDirectory()
        } else {
            file('build/asciidoc').isDirectory()
            expectDeprecationWarnings(result,
                "You are using one or more deprecated Asciidoctor task or plugins. These will be removed in a future release. To help you migrate we have compiled some tips for you based upon your current usage:",
                "  - 'org.asciidoctor.convert' is deprecated. When you have time please switch over to 'org.asciidoctor.jvm.convert'.",
                "Property 'logDocuments' is annotated with @Optional that is not allowed for @Console properties. " +
                    "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. " +
                    "Execution optimizations are disabled due to the failed validation. " +
                    "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.",
            )
        }

        where:
        version << TestedVersions.asciidoctor
    }

    private String pluginIdForVersion(String version) {
        // asciidoctor changed plugin ids after 3.0
        if (VersionNumber.parse(version) >= version3) {
            "org.asciidoctor.jvm.convert"
        } else {
            "org.asciidoctor.convert"
        }
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        TestedVersions.asciidoctor.collectEntries([:]) {
            [pluginIdForVersion(it), Versions.of(it)]
        }
    }

    @Override
    void configureValidation(String pluginId, String version) {
        validatePlugins {
            onPlugin(pluginId) {
                if (VersionNumber.parse(version) < version3) {
                    failsWith([
                        "Type 'AbstractAsciidoctorTask': field 'LAST_GRADLE_WITH_CLASSPATH_LEAKAGE' without corresponding getter has been annotated with @Internal.": WARNING,
                        "Type 'AbstractAsciidoctorTask': field 'configuredOutputOptions' without corresponding getter has been annotated with @Nested.": WARNING,
                        "Type 'AbstractAsciidoctorTask': property 'baseDirConfigured' is not annotated with an input or output annotation.": WARNING,
                        "Type 'AsciidoctorCompatibilityTask': non-property method 'asGemPath()' should not be annotated with: @Optional, @InputDirectory.": WARNING,
                        "Type 'AsciidoctorCompatibilityTask': property 'logDocuments' is annotated with @Optional that is not allowed for @Console properties.": WARNING,
                        "Type 'AsciidoctorPdfTask': property 'baseDirConfigured' is not annotated with an input or output annotation.": WARNING,
                        "Type 'AsciidoctorTask': property 'baseDirConfigured' is not annotated with an input or output annotation.": WARNING,
                        "Type 'AsciidoctorTask': property 'logDocuments' is annotated with @Optional that is not allowed for @Console properties.": WARNING
                    ])
                } else {
                    passes()
                }
            }

            onPlugin('org_asciidoctor_gradle_base_AsciidoctorBasePlugin') {
                if (VersionNumber.parse(version) < version3) {
                    passes()
                } else {
                    failsWith([
                        "Type 'AbstractAsciidoctorBaseTask': field 'configuredOutputOptions' without corresponding getter has been annotated with @Nested.": WARNING,
                        "Type 'AbstractAsciidoctorBaseTask': non-property method 'attributes()' should not be annotated with: @Input.": WARNING,
                        "Type 'AbstractAsciidoctorBaseTask': non-property method 'getDefaultResourceCopySpec()' should not be annotated with: @Internal.": WARNING,
                        "Type 'AbstractAsciidoctorBaseTask': non-property method 'getResourceCopySpec()' should not be annotated with: @Internal.": WARNING,
                        "Type 'SlidesToExportAware': property 'profile' is not annotated with an input or output annotation.": WARNING
                    ])
                }
            }

            onPlugin('org.asciidoctor.gradle.jvm.AsciidoctorJBasePlugin') {
                if (VersionNumber.parse(version) < version3) {
                    skip()
                } else {
                    passes()
                }
            }
        }
    }
}
