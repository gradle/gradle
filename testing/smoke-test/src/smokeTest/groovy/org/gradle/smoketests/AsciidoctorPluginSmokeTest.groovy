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

import org.gradle.integtests.fixtures.modes.UnsupportedWithConfigurationCache

class AsciidoctorPluginSmokeTest extends AbstractPluginValidatingSmokeTest {
    // CC will be supported in plugin 5.x+
    @UnsupportedWithConfigurationCache(because = "https://github.com/asciidoctor/asciidoctor-gradle-plugin/issues/564")
    def 'asciidoctor plugin #version'() {
        given:
        buildFile << """
            plugins {
                id 'org.asciidoctor.jvm.convert' version '${version}'
            }

            ${mavenCentralRepository()}
        """

        file('src/docs/asciidoc/test.adoc') << """
            = Line Break Doc Title
            :hardbreaks:

            Rubies are red,
            Topazes are blue.
            """.stripIndent()

        when:
        runner('asciidoc').deprecations(AsciidocDeprecations) {
            expectAsciiDocDeprecationWarnings()
        }.build()

        then:
        file('build/docs/asciidoc').isDirectory()

        where:
        version << [TestedVersions.asciidoctor]
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        def versions = Versions.of(TestedVersions.asciidoctor)
        [
            "org.asciidoctor.editorconfig": versions,
            "org.asciidoctor.js.convert": versions,
            "org.asciidoctor.jvm.convert": versions,
            "org.asciidoctor.jvm.epub": versions,
            // Plugin broken after JCenter dependency disappeared
            // "org.asciidoctor.jvm.gems" : versions, // Plugin broken after JCenter dependency disappeared
            "org.asciidoctor.jvm.pdf": versions,
        ]
    }

    @Override
    void configureValidation(String pluginId, String version) {
        validatePlugins {
            alwaysPasses()
        }
    }

    @Override
    String getSubprojectExtensionAccess(String testedPluginId, String version) {
        testedPluginId.startsWith("org.asciidoctor.jvm.") ? "asciidoctorj {}" : null
    }

    @Override
    List<String> getSubprojectExtensionDeprecations(String testedPluginId, String version) {
        if (!testedPluginId.startsWith("org.asciidoctor.jvm.")) {
            return []
        }
        return [
            parentMethodInvocationDeprecation('asciidoctorj'),
            getStartParameterDeprecation(),
            getEachDependencyDeprecation()
        ]
    }

    static class AsciidocDeprecations extends BaseDeprecations {
        AsciidocDeprecations(SmokeTestGradleRunner runner) {
            super(runner)
        }

        void expectAsciiDocDeprecationWarnings() {
            runner.expectDeprecationWarning(
                getStartParameterDeprecation(),
                "https://github.com/asciidoctor/asciidoctor-gradle-plugin/issues/751"
            )
            runner.expectDeprecationWarning(
                getEachDependencyDeprecation(),
                "https://github.com/asciidoctor/asciidoctor-gradle-plugin/blob/d969307023d3a6567a70f549f406f28dd23e962c/jvm/src/main/groovy/org/asciidoctor/gradle/jvm/AsciidoctorJExtension.groovy#L650"
            )
            // Asciidoc plugin currently triggers an --enable-native-access warning on Java 24+
            runner.withJdkWarningChecksDisabled()
        }

    }

    private static String getStartParameterDeprecation() {
        "The StartParameter.isConfigurationCacheRequested property has been deprecated. This is scheduled to be removed in Gradle 10. Please use 'configurationCache.requested' property on 'BuildFeatures' service instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_startparameter_is_configuration_cache_requested"
    }

    private static String getEachDependencyDeprecation() {
        "The ResolutionStrategy.eachDependency(Action) method has been deprecated. This is scheduled to be removed in Gradle 10. Please use the dependencySubstitution(Action) method instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#dependency_resolution_deprecations"
    }
}
