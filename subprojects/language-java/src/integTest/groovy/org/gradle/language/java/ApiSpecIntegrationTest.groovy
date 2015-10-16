/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.java

import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.integtests.language.AbstractJvmLanguageIntegrationTest
import org.gradle.language.fixtures.TestJavaComponent

class ApiSpecIntegrationTest extends AbstractJvmLanguageIntegrationTest {

    TestJvmComponent app = new TestJavaComponent()

    def "should succeed when public api specification is absent"() {
        when:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                    }
                }
            }
        """
        then:
        succeeds "assemble"
    }

    def "should succeed when public api specification is present but empty"() {
        when:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        api {
                        }
                    }
                }
            }
        """
        then:
        succeeds "assemble"
    }

    def "should succeed when public api specification is present and fully configured"() {
        when:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        api {
                            exports 'com.example.p1'
                            exports 'com.example.p2'
                        }
                    }
                }
            }
        """
        then:
        succeeds "assemble"
    }

    def "should succeed when public api exports an unnamed package"() {
        when:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        api {
                            exports ''
                        }
                    }
                }
            }
        """
        then:
        succeeds "assemble"
    }

    def "should fail when public api exports an invalid package name"() {
        when:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        api {
                            exports 'com.example.p-1'
                        }
                    }
                }
            }
        """
        then:
        fails "assemble"
        failure.assertHasCause("Invalid public API specification: 'com.example.p-1' is not a valid package name")
    }

    def "should fail when public api exports the same package more than once"() {
        when:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        api {
                            exports 'com.example.p1'
                            exports 'com.example.p1'
                        }
                    }
                }
            }
        """
        then:
        fails "assemble"
        failure.assertHasCause("Invalid public API specification: package 'com.example.p1' has already been exported")
    }
}
