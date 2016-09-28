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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class ExtraPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def 'extra properties are inherited to child projects'() {
        given:
        multiProjectBuild('extra-properties', ['a', 'b']) {
            buildFile << """
                ext.testProp = 'rootValue'

                allprojects {
                    task checkTestProp {
                        doLast {
                            assert testProp == 'rootValue'
                        }
                    }
                }
            """.stripIndent()
        }

        expect:
        succeeds ':a:checkTestProp', ':b:checkTestProp'
    }

    def 'extra properties are inherited to grand child projects'() {
        given:
        multiProjectBuild('extra-properties', ['a', 'b']) {
            buildFile << """
                ext.testProp = 'rootValue'

                allprojects {
                    task checkTestProp {
                        doLast {
                            assert testProp == 'rootValue'
                        }
                    }
                }
            """.stripIndent()

            settingsFile << "include ':a:a1'"
        }

        expect:
        succeeds ':a:a1:checkTestProp'
    }

    @Issue('GRADLE-3530')
    def 'extra properties can be overridden on child projects'() {
        given:
        multiProjectBuild('extra-properties', ['a', 'b']) {
            buildFile << """
                ext.testProp = 'rootValue'

                project(':a') {
                    ext.testProp = 'aValue'
                    allprojects {
                        task checkTestProp {
                            doLast {
                                assert testProp == 'aValue'
                            }
                        }
                    }
                }

                project(':b') {
                    task checkTestProp {
                        doLast {
                            assert testProp == 'rootValue'
                        }
                    }
                }
            """.stripIndent()

            settingsFile << "include ':a:a1'"
        }

        expect:
        succeeds(*(['a', 'b', 'a:a1'].collect { ":${it}:checkTestProp".toString() }))
    }
}
