/*
 * Copyright 2025 the original author or authors.
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

@TargetCoverage({GroovyCoverage.SUPPORTS_GROOVYDOC_JAVA_VERSION})
class GroovyDocJavaVersionIntegrationTest extends MultiVersionIntegrationSpec {

    private static final String GROOVY_DOC_NESTED_CLASS_PATTERN = /Thing\.Other/

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

        // Sealed classes were introduced in Java 17
        file("src/main/groovy/pkg/Thing.java") << """
            package pkg;

            public sealed class Thing {
                public final static class Other extends Thing { }
            }
        """
    }

    def "can provide a java version"() {
        when:
        buildFile << """
            groovydoc {
              javaVersion = JavaLanguageVersion.of(17)
            }
        """

        run "groovydoc"

        then:
        def text = file('build/docs/groovydoc/pkg/Thing.html').text
        text =~ GROOVY_DOC_NESTED_CLASS_PATTERN
    }

}
