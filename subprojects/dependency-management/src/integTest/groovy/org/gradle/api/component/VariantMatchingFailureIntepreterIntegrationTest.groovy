/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.component

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class VariantMatchingFailureIntepreterIntegrationTest extends AbstractIntegrationSpec {
    def "can register a failure interpreter"() {
        buildFile << """
            ${defineCustomInterpreter()}

            dependencies {
                matchingFailureInterpreters.add(new TestVariantMatchingFailureInterpreter())
            }

            assert dependencies.matchingFailureInterpreters.size() == 1
            assert dependencies.matchingFailureInterpreters[0] instanceof TestVariantMatchingFailureInterpreter
        """

        expect:
        succeeds "help"
    }

    def "default JDK mismatch failure interpreter is automatically added by plugin"() {
        buildFile << """
            plugins {
                id 'java-base'
            }

            assert dependencies.matchingFailureInterpreters.size() == 1
            assert dependencies.matchingFailureInterpreters[0] instanceof org.gradle.internal.artifacts.dsl.JDKVersionMismatchFailureInterpreter
        """

        expect:
        succeeds "help"
    }

    def "can register a failure interpreter via a test suite"() {
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${defineCustomInterpreter()}

            testing {
                suites {
                    mySuite(JvmTestSuite) {
                        dependencies {
                            matchingFailureInterpreters.add(new TestVariantMatchingFailureInterpreter())
                        }
                    }
                }
            }

            assert dependencies.matchingFailureInterpreters.size() == 2
            assert dependencies.matchingFailureInterpreters[0] instanceof org.gradle.internal.artifacts.dsl.JDKVersionMismatchFailureInterpreter
            assert dependencies.matchingFailureInterpreters[1] instanceof TestVariantMatchingFailureInterpreter
        """

        expect:
        succeeds "help"
    }

    private String defineCustomInterpreter() {
        return """
            class TestVariantMatchingFailureInterpreter implements VariantMatchingFailureInterpreter {
                @Override
                java.util.Optional<String> process(String producerDisplayName, org.gradle.api.attributes.HasAttributes requested, List<? extends org.gradle.api.attributes.HasAttributes> candidates) {
                    return java.util.Optional.of("Test matcher always matches!")
                }
            }
        """
    }
}
