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

package org.gradle.testing.junitplatform;

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

class JUnitPlatformOptionsIntegrationSpec extends AbstractSampleIntegrationTest {
    @Rule
    TestResources resources = new TestResources(temporaryFolder)

    def "re-executes test when #type is changed in #suiteName"() {
        given:
        resources.maybeCopy("JUnitPlatformOptionsIntegrationSpec")
        buildFile << """
        |testing {
        |   suites {
        |       $suiteDeclaration {
        |           useJUnitJupiter()
        |           targets {
        |               all {
        |                   testTask.configure {
        |                       options {
        |                           ${type} 'fast'
        |                       }
        |                   }
        |               }
        |           }
        |       }
        |   }
        |}""".stripMargin()

        when:
        succeeds ":$task"

        then:
        executedAndNotSkipped ":$task"

        when:
        resources.maybeCopy("JUnitPlatformOptionsIntegrationSpec")
        buildFile << """
        |testing {
        |   suites {
        |       $suiteDeclaration {
        |           useJUnitJupiter()
        |           targets {
        |               all {
        |                   testTask.configure {
        |                       options {
        |                           ${type} 'slow'
        |                       }
        |                   }
        |               }
        |           }
        |       }
        |   }
        |}""".stripMargin()

        and:
        succeeds ":$task"

        then:
        executedAndNotSkipped ":$task"

        where:
        suiteName   | suiteDeclaration              | task        | type
        'test'      | 'test'                        | 'test'      | 'includeTags'
        'test'      | 'test'                        | 'test'      | 'excludeTags'
        'integTest' | 'integTest(JvmTestSuite)'     | 'integTest' | 'includeTags'
        'integTest' | 'integTest(JvmTestSuite)'     | 'integTest' | 'excludeTags'
    }

    def "can set non-options test property for test task in #suiteName prior to calling useJUnitJupiter()"() {
        given:
        resources.maybeCopy("JUnitPlatformOptionsIntegrationSpec")
        buildFile << """
        |testing {
        |   suites {
        |       $suiteDeclaration {
        |           targets {
        |               all {
        |                   testTask.configure {
        |                       minHeapSize = "128m"
        |                   }
        |               }
        |           }
        |           useJUnitJupiter()
        |           targets {
        |               all {
        |                   testTask.configure {
        |                       options {
        |                           ${type} 'fast'
        |                       }
        |                   }
        |               }
        |           }
        |       }
        |   }
        |}""".stripMargin()

        when:
        succeeds ":$task"

        then:
        executedAndNotSkipped ":$task"

        where:
        suiteName   | suiteDeclaration              | task        | type
        'test'      | 'test'                        | 'test'      | 'includeTags'
        'test'      | 'test'                        | 'test'      | 'excludeTags'
        'integTest' | 'integTest(JvmTestSuite)'     | 'integTest' | 'includeTags'
        'integTest' | 'integTest(JvmTestSuite)'     | 'integTest' | 'excludeTags'
    }

    def "can set options in #suiteName lexically prior to calling useJUnitJupiter()"() {
        given:
        resources.maybeCopy("JUnitPlatformOptionsIntegrationSpec")
        buildFile << """
        |testing {
        |   suites {
        |       $suiteDeclaration {
        |           targets {
        |               all {
        |                   testTask.configure {
        |                       options {
        |                           ${type} 'fast'
        |                       }
        |                   }
        |               }
        |           }
        |           useJUnitJupiter()
        |       }
        |   }
        |}""".stripMargin()

        when:
        succeeds ":$task"

        then:
        executedAndNotSkipped ":$task"

        where:
        suiteName   | suiteDeclaration              | task        | type
        'test'      | 'test'                        | 'test'      | 'includeTags'
        'test'      | 'test'                        | 'test'      | 'excludeTags'
        'integTest' | 'integTest(JvmTestSuite)'     | 'integTest' | 'includeTags'
        'integTest' | 'integTest(JvmTestSuite)'     | 'integTest' | 'excludeTags'
    }

    def "can set options on test task directly, outside of default test suite, prior to calling useJUnitJupiter()"() {
        given:
        resources.maybeCopy("JUnitPlatformOptionsIntegrationSpec")
        buildFile << """
        |test {
        |   options {
        |       includeCategories 'org.gradle.CategoryA'
        |   }
        |}
        |
        |testing {
        |   suites {
        |       test {
        |           useJUnitJupiter()
        |           targets {
        |               all {
        |                   testTask.configure {
        |                       options {
        |                           ${type} 'fast'
        |                       }
        |                   }
        |               }
        |           }
        |       }
        |   }
        |}""".stripMargin()

        when:
        succeeds ":test"

        then:
        executedAndNotSkipped ":test"

        where:
        type << ['includeTags', 'excludeTags']
    }
}
