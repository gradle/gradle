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

package org.gradle.testing.testsuites.dependencies

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TestSuitesGroovyDSLDependenciesIntegrationTest extends AbstractIntegrationSpec {
    private versionCatalog = file('gradle', 'libs.versions.toml')

    // region basic functionality
    def 'suites do not share dependencies by default'() {
        given:
        buildFile << """
        plugins {
          id 'java-library'
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
                    testEngines.register(JUnitVintageTestEngine)
                }
            }
        }

        tasks.named('check') {
            dependsOn testing.suites.integTest
        }

        tasks.register('checkConfiguration') {
            dependsOn test, integTest
            def testCompile = configurations.testCompileClasspath
            def testRuntime = configurations.testRuntimeClasspath
            def integTestCompile = configurations.integTestCompileClasspath
            def integTestRuntime = configurations.integTestRuntimeClasspath
            doLast {
                assert testCompile*.name == ['commons-lang3-3.11.jar'] : 'commons-lang3 is an implementation dependency for the default test suite'
                assert testRuntime*.name == ['commons-lang3-3.11.jar'] : 'commons-lang3 is an implementation dependency for the default test suite'
                assert !integTestCompile*.name.contains('commons-lang3-3.11.jar') : 'default test suite dependencies should not leak to integTest'
                assert !integTestRuntime*.name.contains('commons-lang3-3.11.jar') : 'default test suite dependencies should not leak to integTest'
            }
        }
        """

        expect:
        succeeds 'checkConfiguration'
    }

    def "#suiteDesc supports annotationProcessor dependencies"() {
        given: "a suite that uses Google's Auto Value as an example of an annotation processor"
        settingsFile << """rootProject.name = 'Test'"""
        buildFile << """plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    $suiteDeclaration {
                        testEngines.register(JUnitVintageTestEngine)
                        dependencies {
                            implementation 'com.google.auto.value:auto-value-annotations:1.9'
                            annotationProcessor 'com.google.auto.value:auto-value:1.9'
                        }
                    }
                }
            }
            """

        file("src/$suiteName/java/Animal.java") << """
            import com.google.auto.value.AutoValue;

            @AutoValue
            abstract class Animal {
              static Animal create(String name, int numberOfLegs) {
                return new AutoValue_Animal(name, numberOfLegs);
              }

              abstract String name();
              abstract int numberOfLegs();
            }
            """

        file("src/$suiteName/java/AnimalTest.java") << """
            import org.junit.Test;

            import static org.junit.Assert.assertEquals;

            public class AnimalTest {
                @Test
                public void testCreateAnimal() {
                    Animal dog = Animal.create("dog", 4);
                    assertEquals("dog", dog.name());
                    assertEquals(4, dog.numberOfLegs());
                }
            }
            """

        expect: "tests using a class created by running that annotation processor will succeed"
        succeeds(suiteName)

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }
    // endregion basic functionality

    // region dependencies - projects
    def 'default suite has project dependency by default; others do not'() {
        given:
        buildFile << """
        plugins {
          id 'java-library'
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
            def testRuntime = configurations.testRuntimeClasspath
            def integTestRuntime = configurations.integTestRuntimeClasspath
            doLast {
                assert testRuntime*.name == ['commons-lang3-3.11.jar'] : 'commons-lang3 leaks from the production project dependencies'
                assert !integTestRuntime*.name.contains('commons-lang3-3.11.jar') : 'integTest does not implicitly depend on the production project'
            }
        }
        """

        expect:
        succeeds 'checkConfiguration'
    }

    def 'custom suites have project dependency if explicitly set'() {
        given:
        buildFile << """
        plugins {
          id 'java-library'
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
                        implementation project()
                    }
                }
            }
        }

        tasks.named('check') {
            dependsOn testing.suites.integTest
        }

        tasks.register('checkConfiguration') {
            dependsOn test, integTest
            def testCompile = configurations.testCompileClasspath
            def testRuntime = configurations.testRuntimeClasspath
            def integTestRuntime = configurations.integTestRuntimeClasspath
            doLast {
                assert testCompile*.name == ['commons-lang3-3.11.jar'] : 'commons-lang3 leaks from the production project dependencies'
                assert testRuntime*.name == ['commons-lang3-3.11.jar'] : 'commons-lang3 leaks from the production project dependencies'
                assert integTestRuntime*.name.contains('commons-lang3-3.11.jar') : 'integTest explicitly depends on the production project'
            }
        }
        """

        expect:
        succeeds 'checkConfiguration'
    }

    def 'can add dependencies to other projects to #suiteDesc'() {
        given:
        multiProjectBuild('root', ['consumer', 'util']) {
            buildFile << """
                subprojects { apply plugin: 'java-library'}
                project(':util') {
                    dependencies { api 'org.apache.commons:commons-lang3:3.11' }
                }
            """
        }

        file('consumer/build.gradle') << """
            ${mavenCentralRepository()}

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation project(':util')
                        }
                    }
                }
            }

            tasks.register('checkConfiguration') {
                def compile = configurations.${suiteName}CompileClasspath
                doLast {
                    assert compile*.name.contains('commons-lang3-3.11.jar')
                }
            }
        """

        expect:
        succeeds ':consumer:checkConfiguration'


        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def 'can add dependencies to other projects with actions (using exclude) to #suiteDesc'() {
        given:
        multiProjectBuild('root', ['consumer', 'util']) {
            buildFile << """
                subprojects { apply plugin: 'java-library'}
                project(':util') {
                    dependencies { api 'org.apache.commons:commons-lang3:3.11' }
                }
            """
        }

        file('consumer/build.gradle') << """
            ${mavenCentralRepository()}

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(project(':util')) {
                                exclude group: 'org.apache.commons', module: 'commons-lang3'
                            }
                        }
                    }
                }
            }

            tasks.register('checkConfiguration') {
                def compile = configurations.${suiteName}CompileClasspath
                doLast {
                    assert !compile*.name.contains('commons-lang3-3.11.jar')
                }
            }
        """

        expect:
        succeeds ':consumer:checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def 'can add dependencies to other projects with actions (using because) to #suiteDesc'() {
        given:
        multiProjectBuild('root', ['consumer', 'util']) {
            buildFile << """
                subprojects { apply plugin: 'java-library'}
                project(':util') {
                    dependencies { api 'org.apache.commons:commons-lang3:3.11' }
                }
            """
        }

        file('consumer/build.gradle') << """
            ${mavenCentralRepository()}

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(project(':util')) {
                                because 'for testing purposes'
                            }
                        }
                    }
                }
            }

            tasks.register('checkConfiguration') {
                doLast {
                    def deps = configurations.${suiteName}CompileClasspath.allDependencies.withType(ProjectDependency)
                    assert deps.size() == 1
                    assert deps*.reason == ['for testing purposes']
                }
            }
        """

        expect:
        succeeds ':consumer:checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    // endregion dependencies - projects

    // region dependencies - modules (GAV)
    def 'can add dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite using a GAV string'() {
        given:
        buildFile << """
        plugins {
          id 'java-library'
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
                        implementation project()
                        implementation 'com.google.guava:guava:29.0-jre'
                        compileOnly 'javax.servlet:servlet-api:2.5'
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
            def testCompile = configurations.testCompileClasspath
            def testRuntime = configurations.testRuntimeClasspath
            def integTestCompile = configurations.integTestCompileClasspath
            def integTestRuntime = configurations.integTestRuntimeClasspath
            doLast {
                def testCompileClasspathFileNames = testCompile*.name
                def testRuntimeClasspathFileNames = testRuntime*.name

                assert testCompileClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'servlet-api-3.0-alpha-1.jar', 'guava-30.1.1-jre.jar')
                assert !testCompileClasspathFileNames.contains('mysql-connector-java-8.0.26.jar'): 'runtimeOnly dependency'
                assert testRuntimeClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'guava-30.1.1-jre.jar', 'mysql-connector-java-8.0.26.jar')
                assert !testRuntimeClasspathFileNames.contains('servlet-api-3.0-alpha-1.jar'): 'compileOnly dependency'

                def integTestCompileClasspathFileNames = integTestCompile*.name
                def integTestRuntimeClasspathFileNames = integTestRuntime*.name

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

    // TODO: we will probably remove this map-based overload in favor of named arguments, breaking this test in the future
    def 'can add dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite using a GAV map'() {
        given:
        buildFile << """
        plugins {
          id 'java-library'
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
                        implementation(module(group: 'com.google.guava', name: 'guava', version: '30.1.1-jre'))
                        compileOnly(module(group: 'javax.servlet', name: 'servlet-api', version: '3.0-alpha-1'))
                        runtimeOnly(module(group: 'mysql', name: 'mysql-connector-java', version: '8.0.26'))
                    }
                }
                integTest(JvmTestSuite) {
                    // intentionally setting lower versions of the same dependencies on the `test` suite to show that no conflict resolution should be taking place
                    dependencies {
                        implementation project()
                        implementation(module(group: 'com.google.guava', name: 'guava', version: '29.0-jre'))
                        compileOnly(module(group: 'javax.servlet', name: 'servlet-api', version: '2.5'))
                        runtimeOnly(module(group: 'mysql', name: 'mysql-connector-java', version: '6.0.6'))
                    }
                }
            }
        }

        tasks.named('check') {
            dependsOn testing.suites.integTest
        }

        tasks.register('checkConfiguration') {
            dependsOn test, integTest
            def testCompile = configurations.testCompileClasspath
            def testRuntime = configurations.testRuntimeClasspath
            def integTestCompile = configurations.integTestCompileClasspath
            def integTestRuntime = configurations.integTestRuntimeClasspath
            doLast {
                def testCompileClasspathFileNames = testCompile*.name
                def testRuntimeClasspathFileNames = testRuntime*.name

                assert testCompileClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'servlet-api-3.0-alpha-1.jar', 'guava-30.1.1-jre.jar')
                assert !testCompileClasspathFileNames.contains('mysql-connector-java-8.0.26.jar'): 'runtimeOnly dependency'
                assert testRuntimeClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'guava-30.1.1-jre.jar', 'mysql-connector-java-8.0.26.jar')
                assert !testRuntimeClasspathFileNames.contains('servlet-api-3.0-alpha-1.jar'): 'compileOnly dependency'

                def integTestCompileClasspathFileNames = integTestCompile*.name
                def integTestRuntimeClasspathFileNames = integTestRuntime*.name

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

    def 'can add dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite using named args'() {
        given:
        buildFile << """
        plugins {
          id 'java-library'
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
                        implementation(module(group: 'com.google.guava', name: 'guava', version: '30.1.1-jre'))
                        compileOnly(module(group: 'javax.servlet', name: 'servlet-api', version: '3.0-alpha-1'))
                        runtimeOnly(module(group: 'mysql', name: 'mysql-connector-java', version: '8.0.26'))
                    }
                }
                integTest(JvmTestSuite) {
                    // intentionally setting lower versions of the same dependencies on the `test` suite to show that no conflict resolution should be taking place
                    dependencies {
                        implementation project()
                        implementation(module(group: 'com.google.guava', name: 'guava', version: '29.0-jre'))
                        compileOnly(module(group: 'javax.servlet', name: 'servlet-api', version: '2.5'))
                        runtimeOnly(module(group: 'mysql', name: 'mysql-connector-java', version: '6.0.6'))
                    }
                }
            }
        }

        tasks.named('check') {
            dependsOn testing.suites.integTest
        }

        tasks.register('checkConfiguration') {
            dependsOn test, integTest
            def testCompile = configurations.testCompileClasspath
            def testRuntime = configurations.testRuntimeClasspath
            def integTestCompile = configurations.integTestCompileClasspath
            def integTestRuntime = configurations.integTestRuntimeClasspath
            doLast {
                def testCompileClasspathFileNames = testCompile*.name
                def testRuntimeClasspathFileNames = testRuntime*.name

                assert testCompileClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'servlet-api-3.0-alpha-1.jar', 'guava-30.1.1-jre.jar')
                assert !testCompileClasspathFileNames.contains('mysql-connector-java-8.0.26.jar'): 'runtimeOnly dependency'
                assert testRuntimeClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'guava-30.1.1-jre.jar', 'mysql-connector-java-8.0.26.jar')
                assert !testRuntimeClasspathFileNames.contains('servlet-api-3.0-alpha-1.jar'): 'compileOnly dependency'

                def integTestCompileClasspathFileNames = integTestCompile*.name
                def integTestRuntimeClasspathFileNames = integTestRuntime*.name

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

    def 'can add dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite via DependencyHandler using #desc'() {
        given:
        buildFile << """
        plugins {
          id 'java-library'
        }

        ${mavenCentralRepository()}

        testing {
            suites {
                integTest(JvmTestSuite)
            }
        }

        dependencies {
            // production code requires commons-lang3 at runtime, which will leak into tests' runtime classpaths
            implementation 'org.apache.commons:commons-lang3:3.11'

            testImplementation 'com.google.guava:guava:30.1.1-jre'
            testCompileOnly 'javax.servlet:servlet-api:3.0-alpha-1'
            testRuntimeOnly 'mysql:mysql-connector-java:8.0.26'

            // intentionally setting lower versions of the same dependencies on the `test` suite to show that no conflict resolution should be taking place
            integTestImplementation project
            integTestImplementation 'com.google.guava:guava:29.0-jre'
            integTestCompileOnly 'javax.servlet:servlet-api:2.5'
            integTestRuntimeOnly 'mysql:mysql-connector-java:6.0.6'
        }

        tasks.named('check') {
            dependsOn testing.suites.integTest
        }

        tasks.register('checkConfiguration') {
            dependsOn test, integTest
            def testCompile = configurations.testCompileClasspath
            def testRuntime = configurations.testRuntimeClasspath
            def integTestCompile = configurations.integTestCompileClasspath
            def integTestRuntime = configurations.integTestRuntimeClasspath
            doLast {
                def testCompileClasspathFileNames = testCompile*.name
                def testRuntimeClasspathFileNames = testRuntime*.name

                assert testCompileClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'servlet-api-3.0-alpha-1.jar', 'guava-30.1.1-jre.jar')
                assert !testCompileClasspathFileNames.contains('mysql-connector-java-8.0.26.jar'): 'runtimeOnly dependency'
                assert testRuntimeClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'guava-30.1.1-jre.jar', 'mysql-connector-java-8.0.26.jar')
                assert !testRuntimeClasspathFileNames.contains('servlet-api-3.0-alpha-1.jar'): 'compileOnly dependency'

                def integTestCompileClasspathFileNames = integTestCompile*.name
                def integTestRuntimeClasspathFileNames = integTestRuntime*.name

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

    def 'can add dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite via DependencyHandler using a GAV map'() {
        given:
        buildFile << """
        plugins {
          id 'java-library'
        }

        ${mavenCentralRepository()}

        testing {
            suites {
                integTest(JvmTestSuite)
            }
        }

        dependencies {
            // production code requires commons-lang3 at runtime, which will leak into tests' runtime classpaths
            implementation 'org.apache.commons:commons-lang3:3.11'

            testImplementation([group: 'com.google.guava', name: 'guava', version: '30.1.1-jre'])
            testCompileOnly([group: 'javax.servlet', name: 'servlet-api', version: '3.0-alpha-1'])
            testRuntimeOnly([group: 'mysql', name: 'mysql-connector-java', version: '8.0.26'])

            // intentionally setting lower versions of the same dependencies on the `test` suite to show that no conflict resolution should be taking place
            integTestImplementation project
            integTestImplementation([group: 'com.google.guava', name: 'guava', version: '29.0-jre'])
            integTestCompileOnly([group: 'javax.servlet', name: 'servlet-api', version: '2.5'])
            integTestRuntimeOnly([group: 'mysql', name: 'mysql-connector-java', version: '6.0.6'])
        }

        tasks.named('check') {
            dependsOn testing.suites.integTest
        }

        tasks.register('checkConfiguration') {
            dependsOn test, integTest
            def testCompile = configurations.testCompileClasspath
            def testRuntime = configurations.testRuntimeClasspath
            def integTestCompile = configurations.integTestCompileClasspath
            def integTestRuntime = configurations.integTestRuntimeClasspath
            doLast {
                def testCompileClasspathFileNames = testCompile*.name
                def testRuntimeClasspathFileNames = testRuntime*.name

                assert testCompileClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'servlet-api-3.0-alpha-1.jar', 'guava-30.1.1-jre.jar')
                assert !testCompileClasspathFileNames.contains('mysql-connector-java-8.0.26.jar'): 'runtimeOnly dependency'
                assert testRuntimeClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'guava-30.1.1-jre.jar', 'mysql-connector-java-8.0.26.jar')
                assert !testRuntimeClasspathFileNames.contains('servlet-api-3.0-alpha-1.jar'): 'compileOnly dependency'

                def integTestCompileClasspathFileNames = integTestCompile*.name
                def integTestRuntimeClasspathFileNames = integTestRuntime*.name

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

    def 'can add dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite via DependencyHandler using named args'() {
        given:
        buildFile << """
        plugins {
          id 'java-library'
        }

        ${mavenCentralRepository()}

        testing {
            suites {
                integTest(JvmTestSuite)
            }
        }

        dependencies {
            // production code requires commons-lang3 at runtime, which will leak into tests' runtime classpaths
            implementation 'org.apache.commons:commons-lang3:3.11'

           testImplementation group: 'com.google.guava', name: 'guava', version: '30.1.1-jre'
            testCompileOnly group: 'javax.servlet', name: 'servlet-api', version: '3.0-alpha-1'
            testRuntimeOnly group: 'mysql', name: 'mysql-connector-java', version: '8.0.26'

            // intentionally setting lower versions of the same dependencies on the `test` suite to show that no conflict resolution should be taking place
            integTestImplementation project
            integTestImplementation group: 'com.google.guava', name: 'guava', version: '29.0-jre'
            integTestCompileOnly group: 'javax.servlet', name: 'servlet-api', version: '2.5'
            integTestRuntimeOnly group: 'mysql', name: 'mysql-connector-java', version: '6.0.6'
        }

        tasks.named('check') {
            dependsOn testing.suites.integTest
        }

        tasks.register('checkConfiguration') {
            dependsOn test, integTest
            def testCompile = configurations.testCompileClasspath
            def testRuntime = configurations.testRuntimeClasspath
            def integTestCompile = configurations.integTestCompileClasspath
            def integTestRuntime = configurations.integTestRuntimeClasspath
            doLast {
                def testCompileClasspathFileNames = testCompile*.name
                def testRuntimeClasspathFileNames = testRuntime*.name

                assert testCompileClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'servlet-api-3.0-alpha-1.jar', 'guava-30.1.1-jre.jar')
                assert !testCompileClasspathFileNames.contains('mysql-connector-java-8.0.26.jar'): 'runtimeOnly dependency'
                assert testRuntimeClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'guava-30.1.1-jre.jar', 'mysql-connector-java-8.0.26.jar')
                assert !testRuntimeClasspathFileNames.contains('servlet-api-3.0-alpha-1.jar'): 'compileOnly dependency'

                def integTestCompileClasspathFileNames = integTestCompile*.name
                def integTestRuntimeClasspathFileNames = integTestRuntime*.name

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

    def "can add dependency with actions on suite using a #desc"() {
        given:
        buildFile << """
        plugins {
          id 'java-library'
        }

        ${mavenCentralRepository()}

        testing {
            suites {
                test {
                    dependencies {
                        implementation($dependencyNotation) {
                            exclude group: 'commons-collections', module: 'commons-collections'
                        }
                    }
                }
            }
        }

        tasks.register('checkConfiguration') {
            dependsOn test
            def testCompile = configurations.testCompileClasspath
            doLast {
                def testCompileClasspathFileNames = testCompile*.name
                assert testCompileClasspathFileNames.containsAll('commons-beanutils-1.9.4.jar')
                assert !testCompileClasspathFileNames.contains('commons-collections-3.2.2.jar'): 'excluded dependency'
            }
        }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        desc                | dependencyNotation
        'GAV string'        | "'commons-beanutils:commons-beanutils:1.9.4'"
        'GAV map'           | "module(group: 'commons-beanutils', name: 'commons-beanutils', version: '1.9.4')"
    }

    def "can add dependencies using a non-String CharSequence: #type"() {
        given:
        buildFile << """
        import org.apache.commons.lang3.text.StrBuilder;

        buildscript {
            ${mavenCentralRepository()}

            dependencies {
                classpath("org.apache.commons:commons-lang3:3.11")
            }
        }

        plugins {
          id 'java-library'
        }

        ${mavenCentralRepository()}

        $type buf = $creationNotation

        testing {
            suites {
                test {
                    dependencies {
                        implementation(buf)
                    }
                }
            }
        }

        tasks.register('checkConfiguration') {
            dependsOn test
            def testCompile = configurations.testCompileClasspath
            doLast {
                def testCompileClasspathFileNames = testCompile*.name
                assert testCompileClasspathFileNames.containsAll('commons-lang3-3.11.jar')
            }
        }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        type            | creationNotation
        'StringBuilder' | "new StringBuilder('org.apache.commons:commons-lang3:3.11')"
        'GString'       | '"org.apache.commons:commons-lang3:3.${(11 + 11) / (2 + 1 - 1)}"'
    }

    // region multiple GAV strings
    def "can add multiple of GAV dependencies to #suiteDesc - at the top level (varargs)"() {
        given:
        buildFile << """
            plugins {
              id 'java-library'
            }

            ${mavenCentralRepository()}


            testing {
                suites {
                    $suiteDeclaration
                }
            }

            dependencies {
                ${suiteName}Implementation 'org.apache.commons:commons-lang3:3.11', 'com.google.guava:guava:30.1.1-jre'
            }

            tasks.register('checkConfiguration') {
                dependsOn $suiteName
                def compile = configurations.${suiteName}CompileClasspath
                doLast {
                    def ${suiteName}CompileClasspathFileNames = compile*.name
                    assert ${suiteName}CompileClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'guava-30.1.1-jre.jar')
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def "can add multiple GAV dependencies to #suiteDesc - at the top level (list)"() {
        given:
        buildFile << """
            plugins {
              id 'java-library'
            }

            ${mavenCentralRepository()}


            testing {
                suites {
                    $suiteDeclaration
                }
            }

            dependencies {
                ${suiteName}Implementation(['org.apache.commons:commons-lang3:3.11', 'com.google.guava:guava:30.1.1-jre'])
            }

            tasks.register('checkConfiguration') {
                dependsOn $suiteName
                def compile = configurations.${suiteName}CompileClasspath
                doLast {
                    def ${suiteName}CompileClasspathFileNames = compile*.name
                    assert ${suiteName}CompileClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'guava-30.1.1-jre.jar')
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def "can NOT add multiple GAV dependencies to #suiteDesc - no paren or brackets (varargs)"() {
        given:
        buildFile << """
            plugins {
              id 'java-library'
            }

            ${mavenCentralRepository()}


            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation 'org.apache.commons:commons-lang3:3.11', 'com.google.guava:guava:30.1.1-jre'
                        }
                    }
                }
            }

        """

        expect:
        fails 'help'
        result.assertHasErrorOutput("Could not find method implementation() for arguments [org.apache.commons:commons-lang3:3.11, com.google.guava:guava:30.1.1-jre] on object of type org.gradle.api.plugins.jvm.internal.DefaultJvmComponentDependencies")

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def "can NOT add multiple GAV dependencies to #suiteDesc - no brackets (varargs)"() {
        given:
        buildFile << """
            plugins {
              id 'java-library'
            }

            ${mavenCentralRepository()}


            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation('org.apache.commons:commons-lang3:3.11', 'com.google.guava:guava:30.1.1-jre')
                        }
                    }
                }
            }

        """

        expect:
        fails 'help'
        result.assertHasErrorOutput("Could not find method implementation() for arguments [org.apache.commons:commons-lang3:3.11, com.google.guava:guava:30.1.1-jre] on object of type org.gradle.api.plugins.jvm.internal.DefaultJvmComponentDependencies")

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def "can NOT add multiple of GAV dependencies to #suiteDesc - no paren (list)"() {
        given:
        buildFile << """
            plugins {
              id 'java-library'
            }

            ${mavenCentralRepository()}


            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation ['org.apache.commons:commons-lang3:3.11', 'com.google.guava:guava:30.1.1-jre']
                        }
                    }
                }
            }

        """

        expect:
        fails 'help'
        result.assertHasErrorOutput("Could not find method getAt() for arguments [[org.apache.commons:commons-lang3:3.11, com.google.guava:guava:30.1.1-jre]] on DependencyAdder for ${suiteName}Implementation of type org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyAdder_Decorated.")

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def "can NOT add multiple GAV dependencies to #suiteDesc - both paren AND brackets (list)"() {
        given:
        buildFile << """
            plugins {
              id 'java-library'
            }

            ${mavenCentralRepository()}


            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(['org.apache.commons:commons-lang3:3.11', 'com.google.guava:guava:30.1.1-jre'])
                        }
                    }
                }
            }

        """

        expect:
        fails 'help'
        result.assertHasErrorOutput("Could not find method implementation() for arguments [[org.apache.commons:commons-lang3:3.11, com.google.guava:guava:30.1.1-jre]]")

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }
    // endregion multiple GAV strings
    // endregion dependencies - modules (GAV)

    // region dependencies - dependency objects
    def 'can add dependency objects to the implementation, compileOnly and runtimeOnly configurations of a suite'() {
        given :
        buildFile << """
            plugins {
              id 'java-library'
            }

            ${mavenCentralRepository()}

            def commonsLang = dependencies.create 'org.apache.commons:commons-lang3:3.11'
            def servletApi = dependencies.create 'javax.servlet:servlet-api:3.0-alpha-1'
            def mysql = dependencies.create 'mysql:mysql-connector-java:8.0.26'

            testing {
                suites {
                    test {
                        dependencies {
                            implementation commonsLang
                            compileOnly servletApi
                            runtimeOnly mysql
                        }
                    }
                }
            }

            tasks.register('checkConfiguration') {
                def testCompile = configurations.testCompileClasspath
                def testRuntime = configurations.testRuntimeClasspath
                doLast {
                    def testCompileClasspathFileNames = testCompile*.name
                    def testRuntimeClasspathFileNames = testRuntime*.name

                    assert testCompileClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'servlet-api-3.0-alpha-1.jar')
                    assert !testCompileClasspathFileNames.contains('mysql-connector-java-8.0.26.jar'): 'runtimeOnly dependency'
                    assert testRuntimeClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'mysql-connector-java-8.0.26.jar')
                    assert !testRuntimeClasspathFileNames.contains('servlet-api-3.0-alpha-1.jar'): 'compileOnly dependency'
                }
            }
        """

            expect:
            succeeds 'checkConfiguration'
        }

    def 'can add dependency objects with actions (using exclude) to #suiteDesc'() {
        given :
        buildFile << """
            plugins {
              id 'java-library'
            }

            ${mavenCentralRepository()}

            def beanUtils = dependencies.create 'commons-beanutils:commons-beanutils:1.9.4'

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                exclude group: 'commons-collections', module: 'commons-collections'
                            }
                        }
                    }
                }
            }

            tasks.register('checkConfiguration') {
                dependsOn $suiteName
                def compile = configurations.${suiteName}CompileClasspath
                doLast {
                    def ${suiteName}CompileClasspathFileNames = compile*.name

                    assert ${suiteName}CompileClasspathFileNames.contains('commons-beanutils-1.9.4.jar')
                    assert !${suiteName}CompileClasspathFileNames.contains('commons-collections-3.2.2.jar'): 'excluded dependency'
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def 'can add dependency objects with actions (using because) to #suiteDesc'() {
        given :
        buildFile << """
            plugins {
              id 'java-library'
            }

            ${mavenCentralRepository()}

            def beanUtils = dependencies.create 'commons-beanutils:commons-beanutils:1.9.4'

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                because 'for testing purposes'
                            }
                        }
                    }
                }
            }

            tasks.register('checkConfiguration') {
                dependsOn $suiteName
                doLast {
                    def deps = configurations.${suiteName}CompileClasspath.allDependencies.withType(ModuleDependency).matching { it.group == 'commons-beanutils' }
                    assert deps.size() == 1
                    assert deps*.reason == ['for testing purposes']
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }
    // endregion dependencies - dependency objects

    // region dependencies - dependency providers
    def 'can add dependency providers which provide dependency objects to the implementation, compileOnly and runtimeOnly configurations of a suite'() {
        given :
        buildFile << """
            plugins {
              id 'java-library'
            }

            ${mavenCentralRepository()}

            def commonsLang = project.provider(() -> dependencies.create 'org.apache.commons:commons-lang3:3.11')
            def servletApi = project.provider(() -> dependencies.create 'javax.servlet:servlet-api:3.0-alpha-1')
            def mysql = project.provider(() -> dependencies.create 'mysql:mysql-connector-java:8.0.26')

            testing {
                suites {
                    test {
                        dependencies {
                            implementation commonsLang
                            compileOnly servletApi
                            runtimeOnly mysql
                        }
                    }
                }
            }

            tasks.register('checkConfiguration') {
                dependsOn test
                def testCompile = configurations.testCompileClasspath
                def testRuntime = configurations.testRuntimeClasspath
                doLast {
                    def testCompileClasspathFileNames = testCompile*.name
                    def testRuntimeClasspathFileNames = testRuntime*.name

                    assert testCompileClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'servlet-api-3.0-alpha-1.jar')
                    assert !testCompileClasspathFileNames.contains('mysql-connector-java-8.0.26.jar'): 'runtimeOnly dependency'
                    assert testRuntimeClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'mysql-connector-java-8.0.26.jar')
                    assert !testRuntimeClasspathFileNames.contains('servlet-api-3.0-alpha-1.jar'): 'compileOnly dependency'
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'
    }

    def 'can NOT add dependency providers which provide GAVs to the implementation, compileOnly and runtimeOnly configurations of a suite'() {
        given :
        buildFile << """
            plugins {
              id 'java-library'
            }

            ${mavenCentralRepository()}

            def commonsLang = project.provider(() -> 'org.apache.commons:commons-lang3:3.11')
            def servletApi = project.provider(() -> 'javax.servlet:servlet-api:3.0-alpha-1')
            def mysql = project.provider(() -> 'mysql:mysql-connector-java:8.0.26')

            testing {
                suites {
                    test {
                        dependencies {
                            implementation commonsLang
                            compileOnly servletApi
                            runtimeOnly mysql
                        }
                    }
                }
            }

            tasks.register('checkConfiguration') {
                dependsOn test
                doLast {
                    def testCompileClasspathFileNames = testCompile*.name
                    def testRuntimeClasspathFileNames = testRuntime*.name

                    assert testCompileClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'servlet-api-3.0-alpha-1.jar')
                    assert !testCompileClasspathFileNames.contains('mysql-connector-java-8.0.26.jar'): 'runtimeOnly dependency'
                    assert testRuntimeClasspathFileNames.containsAll('commons-lang3-3.11.jar', 'mysql-connector-java-8.0.26.jar')
                    assert !testRuntimeClasspathFileNames.contains('servlet-api-3.0-alpha-1.jar'): 'compileOnly dependency'
                }
            }
        """

        when:
        fails 'checkConfiguration'

        then:
        failureHasCause("Providers of non-Dependency types (java.lang.String) are not supported. Create a Dependency using DependencyFactory first.")
    }

    def 'can add dependency providers which provide dependency objects with actions (using exclude) to #suiteDesc'() {
        given :
        buildFile << """
            plugins {
              id 'java-library'
            }

            ${mavenCentralRepository()}

            def beanUtils = project.provider(() -> dependencies.create 'commons-beanutils:commons-beanutils:1.9.4')

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                exclude group: 'commons-collections', module: 'commons-collections'
                            }
                        }
                    }
                }
            }

            tasks.register('checkConfiguration') {
                dependsOn $suiteName
                def compile = configurations.${suiteName}CompileClasspath
                doLast {
                    def ${suiteName}CompileClasspathFileNames = compile*.name

                    assert ${suiteName}CompileClasspathFileNames.contains('commons-beanutils-1.9.4.jar')
                    assert !${suiteName}CompileClasspathFileNames.contains('commons-collections-3.2.2.jar'): 'excluded dependency'
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def 'can add dependency providers which provide dependency objects with actions (using because) to #suiteDesc'() {
        given :
        buildFile << """
            plugins {
              id 'java-library'
            }

            ${mavenCentralRepository()}

            def beanUtils = project.provider(() -> dependencies.create 'commons-beanutils:commons-beanutils:1.9.4')

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                because 'for testing purposes'
                            }
                        }
                    }
                }
            }

            tasks.register('checkConfiguration') {
                doLast {
                    def deps = configurations.${suiteName}CompileClasspath.allDependencies.withType(ModuleDependency).matching { it.group == 'commons-beanutils' }
                    assert deps.size() == 1
                    assert deps*.reason == ['for testing purposes']
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def 'can NOT add dependency providers which provide GAVs with actions (using excludes) to #suiteDesc'() {
        given :
        buildFile << """
            plugins {
              id 'java-library'
            }

            ${mavenCentralRepository()}

            def beanUtils = project.provider(() -> 'commons-beanutils:commons-beanutils:1.9.4')

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                exclude group: 'commons-collections', module: 'commons-collections'
                            }
                        }
                    }
                }
            }

            tasks.register('checkConfiguration') {
                dependsOn $suiteName
                doLast {
                    def ${suiteName}CompileClasspathFileNames = compile*.name

                    assert ${suiteName}CompileClasspathFileNames.contains('commons-beanutils-1.9.4.jar')
                    assert !${suiteName}CompileClasspathFileNames.contains('commons-collections-3.2.2.jar'): 'excluded dependency'
                }
            }
        """

        when:
        fails 'checkConfiguration'

        then:
        failureHasCause("Providers of non-Dependency types (java.lang.String) are not supported. Create a Dependency using DependencyFactory first.")

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def 'can NOT add dependency providers which provide GAVs with actions (using because) to #suiteDesc'() {
        given :
        buildFile << """
            plugins {
              id 'java-library'
            }

            ${mavenCentralRepository()}

            def beanUtils = project.provider(() -> 'commons-beanutils:commons-beanutils:1.9.4')

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                because 'for testing purposes'
                            }
                        }
                    }
                }
            }

            tasks.register('checkConfiguration') {
                doLast {
                    def deps = configurations.${suiteName}CompileClasspath.allDependencies.withType(ModuleDependency).matching { it.group == 'commons-beanutils' }
                    assert deps.size() == 1
                    assert deps*.reason == ['for testing purposes']
                }
            }
        """

        when:
        fails 'checkConfiguration'

        then:
        failureHasCause("Providers of non-Dependency types (java.lang.String) are not supported. Create a Dependency using DependencyFactory first.")

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }
    // endregion dependencies - dependency providers

    // region dependencies - Version Catalog
    def 'can add dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite via a Version Catalog'() {
        given:
        buildFile << """
        plugins {
          id 'java-library'
        }

        ${mavenCentralRepository()}

        testing {
            suites {
                integTest(JvmTestSuite) {
                    dependencies {
                        implementation libs.guava
                        compileOnly libs.commons.lang3
                        runtimeOnly libs.mysql.connector
                    }
                }
            }
        }

        tasks.named('check') {
            dependsOn testing.suites.integTest
        }

        tasks.register('checkConfiguration') {
            dependsOn test, integTest
            def integTestCompile = configurations.integTestCompileClasspath
            def integTestRuntime = configurations.integTestRuntimeClasspath
            doLast {
                def integTestCompileClasspathFileNames = integTestCompile*.name
                def integTestRuntimeClasspathFileNames = integTestRuntime*.name

                assert integTestCompileClasspathFileNames.containsAll('guava-30.1.1-jre.jar')
                assert integTestRuntimeClasspathFileNames.containsAll('guava-30.1.1-jre.jar')
                assert integTestCompileClasspathFileNames.containsAll('commons-lang3-3.11.jar')
                assert !integTestRuntimeClasspathFileNames.containsAll('commons-lang3-3.11.jar')
                assert !integTestCompileClasspathFileNames.containsAll('mysql-connector-java-6.0.6.jar')
                assert integTestRuntimeClasspathFileNames.containsAll('mysql-connector-java-6.0.6.jar')
            }
        }
        """

        versionCatalog = file('gradle', 'libs.versions.toml') << """
        [versions]
        guava = "30.1.1-jre"
        commons-lang3 = "3.11"
        mysql-connector = "6.0.6"

        [libraries]
        guava = { module = "com.google.guava:guava", version.ref = "guava" }
        commons-lang3 = { module = "org.apache.commons:commons-lang3", version.ref = "commons-lang3" }
        mysql-connector = { module = "mysql:mysql-connector-java", version.ref = "mysql-connector" }
        """.stripIndent(8)

        expect:
        succeeds 'checkConfiguration'
    }

    def 'can add dependencies via a Version Catalog with actions (using exclude) to #suiteDesc'() {
        given:
        buildFile << """
        plugins {
          id 'java-library'
        }

        ${mavenCentralRepository()}

        testing {
            suites {
                $suiteDeclaration {
                    dependencies {
                        implementation(libs.commons.beanutils) {
                            exclude group: 'commons-collections', module: 'commons-collections'
                        }
                    }
                }
            }
        }

        tasks.register('checkConfiguration') {
            dependsOn $suiteName
            def compile = configurations.${suiteName}CompileClasspath
            doLast {
                def ${suiteName}CompileClasspathFileNames = compile*.name

                assert ${suiteName}CompileClasspathFileNames.contains('commons-beanutils-1.9.4.jar')
                assert !${suiteName}CompileClasspathFileNames.contains('commons-collections-3.2.2.jar'): 'excluded dependency'
            }
        }
        """

        versionCatalog = file('gradle', 'libs.versions.toml') << """
        [versions]
        commons-beanutils = "1.9.4"

        [libraries]
        commons-beanutils = { module = "commons-beanutils:commons-beanutils", version.ref = "commons-beanutils" }
        """.stripIndent(8)

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def 'can add dependencies via a Version Catalog with actions (using because) to #suiteDesc'() {
        given:
        buildFile << """
        plugins {
          id 'java-library'
        }

        ${mavenCentralRepository()}

        testing {
            suites {
                $suiteDeclaration {
                    dependencies {
                        implementation(libs.commons.beanutils) {
                            because 'for testing purposes'
                        }
                    }
                }
            }
        }

        tasks.register('checkConfiguration') {
            doLast {
                def deps = configurations.${suiteName}CompileClasspath.allDependencies.withType(ModuleDependency).matching { it.group == 'commons-beanutils' }
                assert deps.size() == 1
                assert deps*.reason == ['for testing purposes']
            }
        }
        """

        versionCatalog = file('gradle', 'libs.versions.toml') << """
        [versions]
        commons-beanutils = "1.9.4"

        [libraries]
        commons-beanutils = { module = "commons-beanutils:commons-beanutils", version.ref = "commons-beanutils" }
        """.stripIndent(8)

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def 'can add dependencies using a Version Catalog bundle to #suiteDesc'() {
        given:
        buildFile << """
        plugins {
          id 'java-library'
        }

        ${mavenCentralRepository()}

        testing {
            suites {
                $suiteDeclaration {
                    dependencies {
                        implementation.bundle libs.bundles.groovy
                    }
                }
            }
        }

        tasks.named('check') {
            dependsOn testing.suites.$suiteName
        }

        tasks.register('checkConfiguration') {
            dependsOn $suiteName
            def compile = configurations.${suiteName}CompileClasspath
            def runtime = configurations.${suiteName}RuntimeClasspath
            doLast {
                def ${suiteName}CompileClasspathFileNames = compile*.name
                def ${suiteName}RuntimeClasspathFileNames = runtime*.name

                assert ${suiteName}CompileClasspathFileNames.containsAll('groovy-json-3.0.5.jar', 'groovy-nio-3.0.5.jar', 'groovy-3.0.5.jar')
                assert ${suiteName}RuntimeClasspathFileNames.containsAll('groovy-json-3.0.5.jar', 'groovy-nio-3.0.5.jar', 'groovy-3.0.5.jar')
            }
        }
        """

        versionCatalog = file('gradle', 'libs.versions.toml') << """
        [versions]
        groovy = "3.0.5"

        [libraries]
        groovy-core = { module = "org.codehaus.groovy:groovy", version.ref = "groovy" }
        groovy-json = { module = "org.codehaus.groovy:groovy-json", version.ref = "groovy" }
        groovy-nio = { module = "org.codehaus.groovy:groovy-nio", version.ref = "groovy" }
        commons-lang3 = { group = "org.apache.commons", name = "commons-lang3", version = { strictly = "[3.8, 4.0[", prefer="3.9" } }

        [bundles]
        groovy = ["groovy-core", "groovy-json", "groovy-nio"]
        """.stripIndent(8)

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def 'can add dependencies using a Version Catalog with a hierarchy of aliases to #suiteDesc'() {
        given:
        buildFile << """
        plugins {
          id 'java-library'
        }

        ${mavenCentralRepository()}

        testing {
            suites {
                $suiteDeclaration {
                    dependencies {
                        implementation libs.commons
                        implementation libs.commons.collections
                        runtimeOnly libs.commons.io
                        runtimeOnly libs.commons.io.csv
                    }
                }
            }
        }

        tasks.named('check') {
            dependsOn testing.suites.$suiteName
        }

        tasks.register('checkConfiguration') {
            dependsOn $suiteName
            def compile = configurations.${suiteName}CompileClasspath
            def runtime = configurations.${suiteName}RuntimeClasspath
            doLast {
                def ${suiteName}CompileClasspathFileNames = compile*.name
                def ${suiteName}RuntimeClasspathFileNames = runtime*.name

                assert ${suiteName}CompileClasspathFileNames.containsAll('commons-lang3-3.12.0.jar', 'commons-collections4-4.4.jar')
                assert ${suiteName}RuntimeClasspathFileNames.containsAll('commons-lang3-3.12.0.jar', 'commons-collections4-4.4.jar', 'commons-io-2.11.0.jar', 'commons-csv-1.9.0.jar')
            }
        }
        """

        versionCatalog = file('gradle', 'libs.versions.toml') << """
        [versions]
        commons-lang = "3.12.0"
        commons-collections = "4.4"
        commons-io = "2.11.0"
        commons-io-csv = "1.9.0"

        [libraries]
        commons = { group = "org.apache.commons", name = "commons-lang3", version.ref = "commons-lang" }
        commons-collections = { group = "org.apache.commons", name = "commons-collections4", version.ref = "commons-collections" }
        commons-io = { group = "commons-io", name = "commons-io", version.ref = "commons-io" }
        commons-io-csv = { group = "org.apache.commons", name = "commons-csv", version.ref = "commons-io-csv" }
        """.stripIndent(8)

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def 'can add dependencies using a Version Catalog defined programmatically to #suiteDesc'() {
        given:
        buildFile << """
        plugins {
          id 'java-library'
        }

        ${mavenCentralRepository()}

        testing {
            suites {
                $suiteDeclaration {
                    dependencies {
                        implementation libs.guava
                        compileOnly libs.commons.lang3
                        runtimeOnly libs.mysql.connector
                    }
                }
            }
        }

        tasks.named('check') {
            dependsOn testing.suites.$suiteName
        }

        tasks.register('checkConfiguration') {
            dependsOn $suiteName
            def compile = configurations.${suiteName}CompileClasspath
            def runtime = configurations.${suiteName}RuntimeClasspath
            doLast {
                def ${suiteName}CompileClasspathFileNames = compile*.name
                def ${suiteName}RuntimeClasspathFileNames = runtime*.name

                assert ${suiteName}CompileClasspathFileNames.containsAll('guava-30.1.1-jre.jar')
                assert ${suiteName}RuntimeClasspathFileNames.containsAll('guava-30.1.1-jre.jar')
                assert ${suiteName}CompileClasspathFileNames.containsAll('commons-lang3-3.11.jar')
                assert !${suiteName}RuntimeClasspathFileNames.containsAll('commons-lang3-3.11.jar')
                assert !${suiteName}CompileClasspathFileNames.containsAll('mysql-connector-java-6.0.6.jar')
                assert ${suiteName}RuntimeClasspathFileNames.containsAll('mysql-connector-java-6.0.6.jar')
            }
        }
        """

        settingsFile << """
        dependencyResolutionManagement {
            versionCatalogs {
                libs {
                    version('guava', '30.1.1-jre')
                    version('commons-lang3', '3.11')
                    version('mysql-connector', '6.0.6')

                    library('guava', 'com.google.guava', 'guava').versionRef('guava')
                    library('commons-lang3', 'org.apache.commons', 'commons-lang3').versionRef('commons-lang3')
                    library('mysql-connector', 'mysql', 'mysql-connector-java').versionRef('mysql-connector')
                }
            }
        }
        """.stripIndent(8)

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }
    // endregion dependencies - Version Catalog

    // region dependencies - platforms
    def "can add a platform dependency to #suiteDesc"() {
        given: "a suite that uses a platform dependency"
        settingsFile << """
            rootProject.name = 'Test'

            include 'platform', 'consumer'
        """

        buildFile << """
            plugins {
                id 'java-platform'
            }

            dependencies {
                constraints {
                    api 'org.apache.commons:commons-lang3:3.8.1'
                }
            }
        """

        file('platform/build.gradle') << """
            plugins {
                id 'java-platform'
            }

            dependencies {
                constraints {
                    api 'org.apache.commons:commons-lang3:3.8.1'
                }
            }
        """

        file('consumer/build.gradle') << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    $suiteDeclaration {
                        testEngines.register(JUnitVintageTestEngine)
                        dependencies {
                            implementation platform(project(':platform'))
                            implementation 'org.apache.commons:commons-lang3'
                        }
                    }
                }
            }
        """

        file("consumer/src/$suiteName/java/SampleTest.java") << """
            import org.apache.commons.lang3.StringUtils;
            import org.junit.Test;

            import static org.junit.Assert.assertTrue;

            public class SampleTest {
                @Test
                public void testCommons() {
                    assertTrue(StringUtils.isAllLowerCase("abc"));
                }
            }
            """

        expect: "tests using a class from that platform will succeed"
        succeeds(suiteName)

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def "can add an enforced platform dependency to #suiteDesc"() {
        given: "a suite that uses an enforced platform dependency"
        settingsFile << """
            rootProject.name = 'Test'

            include 'platform', 'consumer'
        """

        file('platform/build.gradle') << """
            plugins {
                id 'java-platform'
            }

            dependencies {
                constraints {
                    api 'commons-beanutils:commons-beanutils:1.9.0' // depends on commons-collections 3.2.1
                }
            }
        """

        file('consumer/build.gradle') << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    $suiteDeclaration {
                        testEngines.register(JUnitVintageTestEngine)
                        dependencies {
                            implementation enforcedPlatform(project(':platform'))
                            implementation 'commons-collections:commons-collections:3.2.2'
                        }
                    }
                }
            }

            tasks.named('check') {
                dependsOn testing.suites.$suiteName
            }

            tasks.register('checkConfiguration') {
                dependsOn $suiteName
                doLast {
                    def ${suiteName}CompileClasspathFileNames = compile*.name
                    def ${suiteName}RuntimeClasspathFileNames = runtime*.name

                    assert ${suiteName}CompileClasspathFileNames.containsAll('commons-beanutils-1.9.0.jar', 'commons-collections-3.2.1.jar')
                    assert ${suiteName}RuntimeClasspathFileNames.containsAll('commons-beanutils-1.9.0.jar', 'commons-collections-3.2.1.jar')
                    assert !${suiteName}CompileClasspathFileNames.contains('commons-collections-3.2.2.jar')
                    assert !${suiteName}RuntimeClasspathFileNames.contains('commons-collections-3.2.2.jar')
                }
            }
        """

        expect: "tests using a class from that enforcedPlatform will succeed"
        succeeds(suiteName)

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }
    // endregion dependencies - platforms

    // region dependencies - file collections
    def "can add file collection dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite"() {
        given:
        buildFile << """
        plugins {
          id 'java-library'
        }

        testing {
            suites {
                test {
                    dependencies {
                        implementation files('libs/dummy-1.jar')
                        compileOnly files('libs/dummy-2.jar')
                        runtimeOnly files('libs/dummy-3.jar')
                    }
                }
            }
        }

        tasks.register('checkConfiguration') {
            dependsOn test
            def testCompile = configurations.testCompileClasspath
            def testRuntime = configurations.testRuntimeClasspath
            doLast {
                def testCompileClasspathFileNames = testCompile*.name
                def testRuntimeClasspathFileNames = testRuntime*.name

                assert testCompileClasspathFileNames.containsAll('dummy-1.jar')
                assert testRuntimeClasspathFileNames.containsAll('dummy-1.jar')
                assert testCompileClasspathFileNames.containsAll('dummy-2.jar')
                assert !testRuntimeClasspathFileNames.containsAll('dummy-2.jar')
                assert !testCompileClasspathFileNames.containsAll('dummy-3.jar')
                assert testRuntimeClasspathFileNames.containsAll('dummy-3.jar')
            }
        }
        """

        file('libs/dummy-1.jar').createFile()
        file('libs/dummy-2.jar').createFile()
        file('libs/dummy-3.jar').createFile()

        expect:
        succeeds 'checkConfiguration'
    }

    def "can add file collection dependencies to #suiteDesc using fileTree"() {
        given:
        buildFile << """
            plugins {
              id 'java-library'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation fileTree('libs') {
                                include 'dummy-*.jar'
                            }
                        }
                    }
                }
            }

            tasks.register('checkConfiguration') {
                dependsOn $suiteName
                def compile = configurations.${suiteName}CompileClasspath
                doLast {
                    def ${suiteName}CompileClasspathFileNames = compile*.name
                    assert ${suiteName}CompileClasspathFileNames.containsAll('dummy-1.jar', 'dummy-2.jar', 'dummy-3.jar')
                }
            }
        """

        file('libs/dummy-1.jar').createFile()
        file('libs/dummy-2.jar').createFile()
        file('libs/dummy-3.jar').createFile()

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def "can add file collection dependencies #suiteDesc with actions"() {
        given:
        buildFile << """
        plugins {
          id 'java-library'
        }

        ${mavenCentralRepository()}

        List configurationActions = []

        testing {
            suites {
                $suiteDeclaration {
                    dependencies {
                        implementation(files('libs/dummy-1.jar', 'libs/dummy-2.jar')) {
                            configurationActions << 'configured files'
                        }
                    }
                }
            }
        }

        tasks.register('checkConfiguration') {
            dependsOn $suiteName
            def compile = configurations.${suiteName}CompileClasspath
            doLast {
                def ${suiteName}CompileClasspathFileNames = compile*.name
                assert ${suiteName}CompileClasspathFileNames.containsAll('dummy-1.jar', 'dummy-2.jar')

                assert configurationActions.containsAll('configured files')
            }
        }
        """

        file('libs/dummy-1.jar').createFile()
        file('libs/dummy-2.jar').createFile()
        file('libs/dummy-3.jar').createFile()

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }
    // endregion dependencies - file collections

    // region dependencies - self-resolving dependencies
    def "can add localGroovy dependency to #suiteDesc"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    $suiteDeclaration {
                        testEngines.register(JUnitVintageTestEngine)
                        dependencies {
                            implementation localGroovy()
                        }
                    }
                }
            }
        """

        file("src/$suiteName/groovy/Tester.groovy") << """
            import org.junit.Test

            class Tester {
                @Test
                public void testGroovyListOperations() {
                    List myList = ['Jack']
                    myList << 'Jill'
                }
            }
        """

        expect:
        succeeds(suiteName)

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def "can add gradleApi dependency to #suiteDesc"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    $suiteDeclaration {
                        testEngines.register(JUnitVintageTestEngine)
                        dependencies {
                            implementation gradleApi()
                        }
                    }
                }
            }
        """

        file("src/$suiteName/java/Tester.java") << """
            import org.junit.Test;
            import org.gradle.api.file.FileType;

            public class Tester {
                @Test
                public void testGradleApiAvailability() {
                    FileType type = FileType.FILE;
                }
            }
        """

        expect:
        succeeds(suiteName)

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def "can add gradleTestKit dependency to #suiteDesc"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    $suiteDeclaration {
                        testEngines.register(JUnitJupiterTestEngine)
                        dependencies {
                            implementation gradleTestKit()
                        }
                    }
                }
            }
        """

        file("src/$suiteName/java/Tester.java") << """
            import org.gradle.testkit.runner.TaskOutcome;
            import org.junit.jupiter.api.Test;

            public class Tester {
                @Test
                public void testTestKitAvailability()  {
                    TaskOutcome result = TaskOutcome.SUCCESS;
                }
            }
        """

        expect:
        succeeds(suiteName)

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }
    // endregion dependencies - self-resolving dependencies

    // region dependencies - testFixtures
    def "can add testFixture dependency to #suiteDesc"() {
        given: "a multi-project build with a consumer project that depends on the fixtures in a util project for its integration tests"
        multiProjectBuild("root", ["consumer", "util"])
        file("consumer/build.gradle") << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    integrationTest(JvmTestSuite) {
                        testEngines.register(JUnitJupiterTestEngine)
                        dependencies {
                            implementation(testFixtures(project(':util')))
                        }
                    }
                }
            }
        """
        file("util/build.gradle") << """
            plugins {
                id 'java-library'
                id 'java-test-fixtures'
            }
        """

        and: "containing a test which uses a fixture method"
        file("consumer/src/integrationTest/java/org/test/MyTest.java") << """
            package org.test;

            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.Test;

            public class MyTest {
                @Test
                public void testSomething() {
                    Assertions.assertEquals(1, MyFixture.calculateSomething());
                }
            }
        """
        file("util/src/testFixtures/java/org/test/MyFixture.java") << """
            package org.test;

            public class MyFixture {
                public static int calculateSomething() { return 1; }
            }
        """

        expect: "test runs successfully"
        succeeds( ":consumer:integrationTest")

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }

    def "can add testFixture dependency to the same project to #suiteDesc"() {
        given: "a single-project build where a custom test suite depends on the fixtures in that project for its integration tests"
        buildFile << """
            plugins {
                id 'java-library'
                id 'java-test-fixtures'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    integrationTest(JvmTestSuite) {
                        testEngines.register(JUnitJupiterTestEngine)
                        dependencies {
                            implementation(testFixtures(project()))
                        }
                    }
                }
            }
        """

        and: "containing a test which uses a fixture method"
        file("src/integrationTest/java/org/test/MyTest.java") << """
            package org.test;

            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.Test;

            public class MyTest {
                @Test
                public void testSomething() {
                    Assertions.assertEquals(1, MyFixture.calculateSomething());
                }
            }
        """
        file("src/testFixtures/java/org/test/MyFixture.java") << """
            package org.test;

            public class MyFixture {
                public static int calculateSomething() { return 1; }
            }
        """

        expect: "test runs successfully"
        succeeds( ":integrationTest")

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'test'
        'a custom suite'    | 'integTest' | 'integTest(JvmTestSuite)'
    }
    // endregion dependencies - testFixtures
}
