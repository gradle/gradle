/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing.testsuites

import org.gradle.integtests.fixtures.AbstractIntegrationSpec



class TestSuitesDependenciesIntegrationTest extends AbstractIntegrationSpec {

    def 'suites do not share dependencies by default'() {
        given:
        buildFile << """
        plugins {
          id 'java'
        }

        ${mavenCentralRepository()}

        testing {
            suites {
                test {
                    dependencies {
                        implementation 'org.apache.commons:commons-lang3:3.11'
                    }
                }
                integTest(JvmTestSuite) {
                    useJUnit()
                }
            }
        }

        tasks.named('check') {
            dependsOn testing.suites.integTest
        }

        tasks.register('checkConfiguration') {
            dependsOn test, integTest
            doLast {
                assert configurations.testCompileClasspath.files*.name == ['commons-lang3-3.11.jar'] : 'commons-lang3 is an implementation dependency for the default test suite'
                assert configurations.testRuntimeClasspath.files*.name == ['commons-lang3-3.11.jar'] : 'commons-lang3 is an implementation dependency for the default test suite'
                assert !configurations.integTestCompileClasspath.files*.name.contains('commons-lang3-3.11.jar') : 'default test suite dependencies should not leak to integTest'
                assert !configurations.integTestRuntimeClasspath.files*.name.contains('commons-lang3-3.11.jar') : 'default test suite dependencies should not leak to integTest'
            }
        }
        """

        expect:
        succeeds 'checkConfiguration'
    }

    def 'default test suite has project dependency by default; others do not'() {
        given:
        buildFile << """
        plugins {
          id 'java'
        }

        ${mavenCentralRepository()}

        dependencies {
            // production code requires commons-lang3 at runtime, which will leak into tests' runtime classpaths
            implementation 'org.apache.commons:commons-lang3:3.11'
        }

        testing {
            suites {
                integTest(JvmTestSuite)
            }
        }

        tasks.named('check') {
            dependsOn testing.suites.integTest
        }

        tasks.register('checkConfiguration') {
            dependsOn test, integTest
            doLast {
                assert configurations.testRuntimeClasspath.files*.name == ['commons-lang3-3.11.jar'] : 'commons-lang3 leaks from the production project dependencies'
                assert !configurations.integTestRuntimeClasspath.files*.name.contains('commons-lang3-3.11.jar') : 'integTest does not implicitly depend on the production project'
            }
        }
        """

        expect:
        succeeds 'checkConfiguration'
    }

    def 'non-default default test suites have project dependency if explicitly set'() {
        given:
        buildFile << """
        plugins {
          id 'java'
        }

        ${mavenCentralRepository()}

        dependencies {
            // production code requires commons-lang3 at runtime, which will leak into tests' runtime classpaths
            implementation 'org.apache.commons:commons-lang3:3.11'
        }

        testing {
            suites {
                integTest(JvmTestSuite) {
                    dependencies {
                        implementation project
                    }
                }
            }
        }

        tasks.named('check') {
            dependsOn testing.suites.integTest
        }

        tasks.register('checkConfiguration') {
            dependsOn test, integTest
            doLast {
                assert configurations.testCompileClasspath.files*.name == ['commons-lang3-3.11.jar'] : 'commons-lang3 leaks from the production project dependencies'
                assert configurations.testRuntimeClasspath.files*.name == ['commons-lang3-3.11.jar'] : 'commons-lang3 leaks from the production project dependencies'
                assert configurations.integTestRuntimeClasspath.files*.name.contains('commons-lang3-3.11.jar') : 'integTest explicitly depends on the production project'
            }
        }
        """

        expect:
        succeeds 'checkConfiguration'
    }

    def 'user can add dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite'() {
        given:
        buildFile << """
        plugins {
          id 'java'
        }

        ${mavenCentralRepository()}

        dependencies {
            // production code requires commons-lang3 at runtime, which will leak into tests' runtime classpaths
            implementation 'org.apache.commons:commons-lang3:3.11'
        }

        testing {
            suites {
                test {
                    dependencies {
                        implementation 'com.google.guava:guava:30.1.1-jre'
                        compileOnly 'javax.servlet:servlet-api:3.0-alpha-1'
                        runtimeOnly 'mysql:mysql-connector-java:8.0.26'
                    }
                }
                integTest(JvmTestSuite) {
                    // intentionally setting lower versions of the same dependencies on the `test` suite to show that no conflict resolution should be taking place
                    dependencies {
                        implementation project
                        implementation 'com.google.guava:guava:29.0-jre'
                        compileOnly  'javax.servlet:servlet-api:2.5'
                        runtimeOnly 'mysql:mysql-connector-java:6.0.6'
                    }
                }
            }
        }

        tasks.named('check') {
            dependsOn testing.suites.integTest
        }

        tasks.register('checkConfiguration') {
            dependsOn test, integTest
            doLast {
                def testCompileClasspathFileNames = configurations.testCompileClasspath.files*.name
                def testRuntimeClasspathFileNames = configurations.testRuntimeClasspath.files*.name

                assert testCompileClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'servlet-api-3.0-alpha-1.jar', 'guava-30.1.1-jre.jar')
                assert !testCompileClasspathFileNames.contains('mysql-connector-java-8.0.26.jar'): 'runtimeOnly dependency'
                assert testRuntimeClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'guava-30.1.1-jre.jar', 'mysql-connector-java-8.0.26.jar')
                assert !testRuntimeClasspathFileNames.contains('servlet-api-3.0-alpha-1.jar'): 'compileOnly dependency'

                def integTestCompileClasspathFileNames = configurations.integTestCompileClasspath.files*.name
                def integTestRuntimeClasspathFileNames = configurations.integTestRuntimeClasspath.files*.name

                assert integTestCompileClasspathFileNames.containsAll('servlet-api-2.5.jar', 'guava-29.0-jre.jar')
                assert !integTestCompileClasspathFileNames.contains('commons-lang3-3.11.jar') : 'implementation dependency of project, should not leak to integTest'
                assert !integTestCompileClasspathFileNames.contains('mysql-connector-java-6.0.6.jar'): 'runtimeOnly dependency'
                assert integTestRuntimeClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'guava-29.0-jre.jar', 'mysql-connector-java-6.0.6.jar')
                assert !integTestRuntimeClasspathFileNames.contains('servlet-api-2.5.jar'): 'compileOnly dependency'
            }
        }
        """

        expect:
        succeeds 'checkConfiguration'
    }

}
