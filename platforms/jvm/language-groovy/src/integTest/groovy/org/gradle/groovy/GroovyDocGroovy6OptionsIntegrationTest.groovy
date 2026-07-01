/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.groovy

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.GroovyCoverage

import static org.gradle.util.internal.GroovyDependencyUtil.groovyModuleDependency

/**
 * Coverage for the Groovydoc options introduced in Groovy 6.0.0
 * ({@code showInternal}, {@code noIndex}, {@code noDeprecatedList}, {@code noHelp},
 * {@code syntaxHighlighter}, {@code theme}, {@code preLanguage} and additional stylesheets).
 *
 * <p>These options are silently ignored by earlier Groovy versions, so this spec only runs against
 * Groovy 6.0.0 and later. The current test matrix contains no such version yet, so the spec is
 * currently skipped; it will activate automatically once a Groovy 6.x release is added to
 * {@link GroovyCoverage}.</p>
 */
@TargetCoverage({ GroovyCoverage.SUPPORTS_GROOVYDOC_GROOVY6_OPTIONS })
class GroovyDocGroovy6OptionsIntegrationTest extends MultiVersionIntegrationSpec {

    def setup() {
        buildFile << """
            plugins {
                id("groovy")
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation "${groovyModuleDependency("groovy", versionNumber)}"
            }
        """

        file("src/main/groovy/pkg/Thing.groovy") << """
            package pkg

            /** A thing. */
            class Thing {
                /** Greets. */
                void greet() {}
            }
        """
    }

    def "can suppress the index, help and deprecated-list pages"() {
        when:
        buildFile << """
            groovydoc {
                noIndex = true
                noHelp = true
                noDeprecatedList = true
            }
        """

        run "groovydoc"

        then:
        // The documented class page is always generated.
        file('build/docs/groovydoc/pkg/Thing.html').exists()
        // The suppressed auxiliary pages are not.
        !file('build/docs/groovydoc/index-all.html').exists()
        !file('build/docs/groovydoc/help-doc.html').exists()
        !file('build/docs/groovydoc/deprecated-list.html').exists()
    }

    def "can add an additional stylesheet to the generated documentation"() {
        given:
        file("extra.css") << "/* custom */ body { color: rebeccapurple; }"

        when:
        buildFile << """
            groovydoc {
                additionalStylesheets.from(file("extra.css"))
            }
        """

        run "groovydoc"

        then:
        // The extra stylesheet is copied into the output, preserving its name.
        file('build/docs/groovydoc/extra.css').text.contains("rebeccapurple")
    }

    def "accepts theme, syntax highlighter, preLanguage and showInternal options"() {
        when:
        buildFile << """
            groovydoc {
                theme = "dark"
                syntaxHighlighter = "prism"
                preLanguage = "groovy"
                showInternal = true
            }
        """

        then:
        succeeds "groovydoc"
        file('build/docs/groovydoc/pkg/Thing.html').exists()
    }

}
