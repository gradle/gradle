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
            ${defineCustomVariantFailureInterpreterClass()}

            dependencies {
                matchingFailureInterpreters.add(new MyVariantMatchingFailureInterpreter())
            }

            assert dependencies.matchingFailureInterpreters.size() == 1
            assert dependencies.matchingFailureInterpreters[0] instanceof MyVariantMatchingFailureInterpreter
        """

        expect:
        succeeds "help"
    }

    def "can register a failure interpreter via a test suite"() {
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${defineCustomVariantFailureInterpreterClass()}

            testing {
                suites {
                    mySuite(JvmTestSuite) {
                        dependencies {
                            matchingFailureInterpreters.add(new MyVariantMatchingFailureInterpreter())
                        }
                    }
                }
            }

            assert dependencies.matchingFailureInterpreters.size() == 1
            assert dependencies.matchingFailureInterpreters[0] instanceof MyVariantMatchingFailureInterpreter
        """

        expect:
        succeeds "help"
    }

    private String defineCustomVariantFailureInterpreterClass() {
        return """
            class MyVariantMatchingFailureInterpreter implements VariantMatchingFailureInterpreter {
                @Override
                java.util.Optional<String> process(String producerDisplayName, org.gradle.api.attributes.HasAttributes requested, List<? extends org.gradle.api.attributes.HasAttributes> candidates) {
                    return java.util.Optional.of("Matched!")
                }
            }
        """
    }
}
