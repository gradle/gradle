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

package org.gradle.groovy


import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.GroovyCoverage
import spock.lang.Issue

import static org.gradle.util.internal.GroovyDependencyUtil.groovyModuleDependency

@TargetCoverage({ GroovyCoverage.SUPPORTS_DISABLING_AST_TRANSFORMATIONS })
@Issue('https://github.com/gradle/gradle/issues/1031')
class GroovyTransformationIntegrationTest extends MultiVersionIntegrationSpec {

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
    }

    def "Can configure disabled AST transformations"() {
        setup:
        buildFile << """
            compileGroovy {
                groovyOptions.disabledGlobalASTTransformations = [] // remove the default
            }
        """
        file("src/main/groovy/Person.groovy") << """
            @Grab(group='commons-lang', module='commons-lang', version='2.4')
        """

        expect:
        fails'compileGroovy'
    }

    def "Groovy compilation ignores @Grab annotation transformation by default"() {
        setup:
        file("src/main/groovy/Person.groovy") << """
            @Grab(group='commons-lang', module='commons-lang', version='2.4')
            class Person { }
        """

        expect:
        succeeds 'compileGroovy'
    }

    def "Dependencies defined @Grab annotation are not present on the classpath"() {
        setup:
        file("src/main/groovy/Person.groovy") << """
            @Grab(group='commons-lang', module='commons-lang', version='2.4')
            import org.apache.commons.lang.WordUtils
            class Person {
                String foo() {
                    "Hello \${WordUtils.capitalize('world')}"
                }
            }
        """

        expect:
        fails 'compileGroovy'
        failure.error.contains("unable to resolve class org.apache.commons.lang.WordUtils")
    }
}
