/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.testing.testsuites.transparency

class DefaultSuiteRuntimeDepIntegrationTest extends AbstractRuntimeDepTestSuitesTransparencyIntegrationTest {
    def "default suite has project, but lacks collection dep and fails"() {
        expect:
        failsAtRuntimeDueToMissingCollectionsDependency("test")
    }

    def "default suite with runtimeOnly collections dep succeeds"() {
        given:
        buildFile << """
            testing {
                suites {
                    test {
                        dependencies {
                            runtimeOnly 'commons-collections:commons-collections:3.2.1'
                        }
                    }
                }
            }
        """.stripIndent()

        expect:
        successfullyRunsSuite("test")
    }

    def "default suite with project compileOnly collections dep fails"() {
        given:
            buildFile << """
            dependencies {
                compileOnly 'commons-collections:commons-collections:3.2.1'
            }
        """.stripIndent()

        expect:
        failsAtRuntimeDueToMissingCollectionsDependency("test")
    }

    def "default suite with project implementation collections dep succeeds"() {
        buildFile << """
            dependencies {
                implementation 'commons-collections:commons-collections:3.2.1'
            }
        """.stripIndent()

        expect:
        successfullyRunsSuite("test")
    }

    def "default suite with project testImplementation collections dep succeeds"() {
        buildFile << """
            dependencies {
                testImplementation 'commons-collections:commons-collections:3.2.1'
            }
        """.stripIndent()

        expect:
        successfullyRunsSuite("test")
    }
}
