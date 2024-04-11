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

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.util.internal.VersionNumber

import static org.gradle.api.internal.DocumentationRegistry.BASE_URL

class AsciidoctorPluginSmokeTest extends AbstractPluginValidatingSmokeTest {
    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
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
            expectAsciiDocDeprecationWarnings(version)
        }.build()

        then:
        file('build/docs/asciidoc').isDirectory()

        where:
        version << TestedVersions.asciidoctor
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        TestedVersions.asciidoctor.collectEntries([:]) { version ->
            def base = [
                "org.asciidoctor.editorconfig",
                "org.asciidoctor.js.convert",
                "org.asciidoctor.jvm.convert",
                "org.asciidoctor.jvm.epub",
                "org.asciidoctor.jvm.gems",
                "org.asciidoctor.jvm.pdf",
            ].collectEntries { plugin ->
                [(plugin): Versions.of(version)]
            }
            if(version.startsWith("3")) {
                base + [
                    "org.asciidoctor.decktape",
                    "org.asciidoctor.jvm.leanpub",
                    "org.asciidoctor.jvm.leanpub.dropbox-copy",
                    "org.asciidoctor.jvm.revealjs",
                ].collectEntries { plugin ->
                    [(plugin): Versions.of(version)]
                }
            } else {
                base
            }
        }
    }

    @Override
    void configureValidation(String pluginId, String version) {
        validatePlugins {
            alwaysPasses()
        }
    }

    static class AsciidocDeprecations extends BaseDeprecations {
        AsciidocDeprecations(SmokeTestGradleRunner runner) {
            super(runner)
        }

        void expectAsciiDocDeprecationWarnings(String asciidoctorVersion) {
            def versionNumber = VersionNumber.parse(asciidoctorVersion)
            runner.expectLegacyDeprecationWarningIf(
                versionNumber.major < 4,
                "The org.gradle.util.CollectionUtils type has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: ${BASE_URL}/userguide/upgrading_version_8.html#org_gradle_util_reports_deprecations"
            )

            runner.expectLegacyDeprecationWarningIf(
                versionNumber.major < 4,
                "The JavaExecSpec.main property has been deprecated." +
                    " This is scheduled to be removed in Gradle 9.0." +
                    " Property was automatically upgraded to the lazy version." +
                    " Please use the mainClass property instead." +
                    " ${String.format(DocumentationRegistry.RECOMMENDATION, "information", "${BASE_URL}/dsl/org.gradle.process.JavaExecSpec.html#org.gradle.process.JavaExecSpec:main")}"
            )
        }
    }
}
