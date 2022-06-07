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

class CustomSuiteCompileDepIntegrationTest extends AbstractCompileDepTestSuitesTransparencyIntegrationTest {
    // region default transparency
    def "custom suite without project dep fails"() {
        expect:
        failsAtCompileDueToMissingProjectClasses("compileIntegTestJava")
    }
    // endregion default transparency

    // region consumer transparency
    def "custom consumer suite fails"() {
        given:
        buildFile << """
            testing.suites.integTest.transparencyLevel(ProjectTransparencyLevel.CONSUMER)
        """.stripIndent()

        expect:
        failsAtRuntimeDueToMissingCollectionsDependencyNoClassDefFound("integTest")
    }

    def "custom consumer suite with project implementation collection dep succeeds"() {
        given:
        buildFile << """
            dependencies {
                implementation 'commons-collections:commons-collections:3.2.1'
            }

            testing.suites.integTest.transparencyLevel(ProjectTransparencyLevel.CONSUMER)
        """.stripIndent()

        expect:
        successfullyRunsSuite("integTest")
    }

    def "custom consumer suite with project testImplementation collection dep fails"() {
        given:
        buildFile << """
            dependencies {
                testImplementation 'commons-collections:commons-collections:3.2.1'
            }

            testing.suites.integTest.transparencyLevel(ProjectTransparencyLevel.CONSUMER)
        """.stripIndent()

        expect:
        failsAtRuntimeDueToMissingCollectionsDependencyNoClassDefFound("integTest")
    }

    def "custom consumer suite with project runtimeOnly collection dep succeeds"() {
        given:
        buildFile << """
            dependencies {
                runtimeOnly 'commons-collections:commons-collections:3.2.1'
            }

            testing.suites.integTest.transparencyLevel(ProjectTransparencyLevel.CONSUMER)
        """.stripIndent()

        expect:
        successfullyRunsSuite("integTest")
    }

    def "custom consumer suite with project testRuntimeOnly collection dep fails"() {
        given:
        buildFile << """
            dependencies {
                testRuntimeOnly 'commons-collections:commons-collections:3.2.1'
            }

            testing.suites.integTest.transparencyLevel(ProjectTransparencyLevel.CONSUMER)
        """.stripIndent()

        expect:
        failsAtRuntimeDueToMissingCollectionsDependencyNoClassDefFound("integTest")
    }
    // endregion consumer transparency

    // region test consumer transparency
    def "custom test consumer suite fails"() {
        given:
        buildFile << """
            testing.suites.integTest.transparencyLevel(ProjectTransparencyLevel.TEST_CONSUMER)
        """.stripIndent()

        expect:
        failsAtRuntimeDueToMissingCollectionsDependencyNoClassDefFound("integTest")
    }

    def "custom test consumer suite with project implementation collection dep succeeds"() {
        given:
        buildFile << """
            dependencies {
                implementation 'commons-collections:commons-collections:3.2.1'
            }

            testing.suites.integTest.transparencyLevel(ProjectTransparencyLevel.TEST_CONSUMER)
        """.stripIndent()

        expect:
        successfullyRunsSuite("integTest")
    }

    def "custom test consumer suite with project testImplementation collection dep succeeds"() {
        given:
        buildFile << """
            dependencies {
                testImplementation 'commons-collections:commons-collections:3.2.1'
            }

            testing.suites.integTest.transparencyLevel(ProjectTransparencyLevel.TEST_CONSUMER)
        """.stripIndent()

        expect:
        successfullyRunsSuite("integTest")
    }

    def "custom test consumer suite with project runtimeOnly collection dep succeeds"() {
        given:
        buildFile << """
            dependencies {
                runtimeOnly 'commons-collections:commons-collections:3.2.1'
            }

            testing.suites.integTest.transparencyLevel(ProjectTransparencyLevel.TEST_CONSUMER)
        """.stripIndent()

        expect:
        successfullyRunsSuite("integTest")
    }

    def "custom test consumer suite with project testRuntimeOnly collection dep fails"() {
        given:
        buildFile << """
            dependencies {
                testRuntimeOnly 'commons-collections:commons-collections:3.2.1'
            }

            testing.suites.integTest.transparencyLevel(ProjectTransparencyLevel.TEST_CONSUMER)
        """.stripIndent()

        expect:
        successfullyRunsSuite("integTest")
    }
    // endregion test consumer transparency

    // region internal transparency
    def "custom internal consumer fails"() {
        given:
        buildFile << """
            testing.suites.integTest.transparencyLevel(ProjectTransparencyLevel.INTERNAL_CONSUMER)
        """.stripIndent()

        expect:
        failsAtRuntimeDueToMissingCollectionsDependencyNoClassDefFound("integTest")
    }

    def "custom internal consumer with project implementation collection dep succeeds"() {
        given:
        buildFile << """
            dependencies {
                implementation 'commons-collections:commons-collections:3.2.1'
            }

            testing.suites.integTest.transparencyLevel(ProjectTransparencyLevel.INTERNAL_CONSUMER)
        """.stripIndent()

        expect:
        successfullyRunsSuite("integTest")
    }

    def "custom internal consumer with project test implementation collection dep fails"() {
        given:
        buildFile << """
            dependencies {
                testImplementation 'commons-collections:commons-collections:3.2.1'
            }

            testing.suites.integTest.transparencyLevel(ProjectTransparencyLevel.INTERNAL_CONSUMER)
        """.stripIndent()

        expect:
        failsAtRuntimeDueToMissingCollectionsDependencyNoClassDefFound("integTest")
    }

    def "custom internal consumer with project runtimeOnly collection dep succeeds"() {
        given:
        buildFile << """
            dependencies {
                runtimeOnly 'commons-collections:commons-collections:3.2.1'
            }

            testing.suites.integTest.transparencyLevel(ProjectTransparencyLevel.INTERNAL_CONSUMER)
        """.stripIndent()

        expect:
        successfullyRunsSuite("integTest")
    }
    // endregion internal transparency

    // region project classes only transparency
    def "custom project classes only consumer fails"() {
        given:
        buildFile << """
            testing.suites.integTest.transparencyLevel(ProjectTransparencyLevel.PROJECT_CLASSES_ONLY)
        """.stripIndent()

        expect:
        failsAtRuntimeDueToMissingCollectionsDependencyNoClassDefFound("integTest")
    }

    def "custom project classes only consumer with project implementation collection dep fails"() {
        given:
        buildFile << """
            dependencies {
                implementation 'commons-collections:commons-collections:3.2.1'
            }

            testing.suites.integTest.transparencyLevel(ProjectTransparencyLevel.PROJECT_CLASSES_ONLY)
        """.stripIndent()

        expect:
        failsAtRuntimeDueToMissingCollectionsDependencyNoClassDefFound("integTest")
    }

    def "custom projectOnly consumer with suite runtimeOnly collections deps succeeds"() {
        given:
        buildFile << """
            testing {
                suites {
                    integTest {
                        dependencies {
                            runtimeOnly 'commons-collections:commons-collections:3.2.1'
                        }
                    }
                }
            }

            testing.suites.integTest.transparencyLevel(ProjectTransparencyLevel.PROJECT_CLASSES_ONLY)
        """.stripIndent()

        expect:
        successfullyRunsSuite("integTest")
    }
    // endregion project classes only transparency
}
