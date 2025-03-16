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

package org.gradle.testing.testsuites.dependencies


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.dsl.GradleDsl

class TestSuitesKotlinDSLDependenciesIntegrationTest extends AbstractIntegrationSpec {
    // region basic functionality
    def 'suites do not share dependencies by default'() {
        given:
        buildKotlinFile << """
            plugins {
                `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    val test by getting(JvmTestSuite::class) {
                        dependencies {
                            implementation("org.apache.commons:commons-lang3:3.11")
                        }
                    }
                    val integTest by registering(JvmTestSuite::class) {
                        useJUnit()
                    }
                }
            }

            tasks.named("check") {
                dependsOn(testing.suites.named("integTest"))
            }

            tasks.register("checkConfiguration") {
                dependsOn("test", "integTest")

                val testCompileClasspathFiles = configurations.getByName("testCompileClasspath").files
                val testRuntimeClasspathFiles = configurations.getByName("testRuntimeClasspath").files
                val integTestCompileClasspathFiles = configurations.getByName("integTestCompileClasspath").files
                val integTestRuntimeClasspathFiles = configurations.getByName("integTestRuntimeClasspath").files

                doLast {
                    assert(testCompileClasspathFiles.map { it.name }.equals(listOf("commons-lang3-3.11.jar"))) { "commons-lang3 is an implementation dependency for the default test suite" }
                    assert(testRuntimeClasspathFiles.map { it.name }.equals(listOf("commons-lang3-3.11.jar"))) { "commons-lang3 is an implementation dependency for the default test suite" }
                    assert(!integTestCompileClasspathFiles.map { it.name }.contains("commons-lang3-3.11.jar")) { "default test suite dependencies should not leak to integTest" }
                    assert(!integTestRuntimeClasspathFiles.map { it.name }.contains("commons-lang3-3.11.jar")) { "default test suite dependencies should not leak to integTest" }
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'
    }

    def "#suiteDesc supports annotationProcessor dependencies"() {
        given: "a suite that uses Google's Auto Value as an example of an annotation processor"
        settingsKotlinFile << """
            rootProject.name = "Test"
        """

        buildKotlinFile << """
            plugins {
                `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        useJUnit()
                        dependencies {
                            implementation("com.google.auto.value:auto-value-annotations:1.9")
                            annotationProcessor("com.google.auto.value:auto-value:1.9")
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
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }
    // endregion basic functionality

    // region dependencies - projects
    def 'default suite has project dependency by default; others do not'() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        dependencies {
            // production code requires commons-lang3 at runtime, which will leak into tests' runtime classpaths
            implementation("org.apache.commons:commons-lang3:3.11")
        }

        testing {
            suites {
                val integTest by registering(JvmTestSuite::class)
            }
        }

        tasks.named("check") {
            dependsOn(testing.suites.named("integTest"))
        }

        tasks.register("checkConfiguration") {
            dependsOn("test", "integTest")

            val testRuntimeClasspathFileNames = configurations.getByName("testRuntimeClasspath").files.map { it.name }
            val integTestRuntimeClasspathFileNames = configurations.getByName("integTestRuntimeClasspath").files.map { it.name }

            doLast {
                assert(testRuntimeClasspathFileNames.equals(listOf("commons-lang3-3.11.jar"))) { "commons-lang3 leaks from the production project dependencies" }
                assert(!integTestRuntimeClasspathFileNames.contains("commons-lang3-3.11.jar")) { "integTest does not implicitly depend on the production project" }
            }
        }
        """

        expect:
        succeeds 'checkConfiguration'
    }

    def 'custom suites have project dependency if explicitly set'() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        dependencies {
            // production code requires commons-lang3 at runtime, which will leak into tests' runtime classpaths
            implementation("org.apache.commons:commons-lang3:3.11")
        }

        testing {
            suites {
                val integTest by registering(JvmTestSuite::class) {
                    dependencies {
                        implementation(project())
                    }
                }
            }
        }

        tasks.named("check") {
            dependsOn(testing.suites.named("integTest"))
        }

        tasks.register("checkConfiguration") {
            dependsOn("test", "integTest")

            val testCompileClasspathFileNames = configurations.getByName("testCompileClasspath").files.map { it.name }
            val testRuntimeClasspathFileNames = configurations.getByName("testRuntimeClasspath").files.map { it.name }
            val integTestRuntimeClasspathFileNames = configurations.getByName("integTestRuntimeClasspath").files.map { it.name }

            doLast {
                assert(testCompileClasspathFileNames.equals(listOf("commons-lang3-3.11.jar"))) { "commons-lang3 leaks from the production project dependencies" }
                assert(testRuntimeClasspathFileNames.equals(listOf("commons-lang3-3.11.jar"))) { "commons-lang3 leaks from the production project dependencies" }
                assert(integTestRuntimeClasspathFileNames.contains("commons-lang3-3.11.jar")) { "integTest explicitly depends on the production project" }
            }
        }
        """

        expect:
        succeeds 'checkConfiguration'
    }

    def 'can add dependencies to other projects to #suiteDesc'() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"

            include("consumer", "util")
        """

        buildKotlinFile << """
            allprojects {
                group = "org.test"
                version = "1.0"
            }

            subprojects {
                apply(plugin = "java-library")
            }
        """

        file('util/build.gradle.kts') << """
            dependencies {
                api("org.apache.commons:commons-lang3:3.11")
            }
        """

        file('consumer/build.gradle.kts') << """
            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(project(":util"))
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                val compileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }

                doLast {
                    assert(compileClasspathFileNames.contains("commons-lang3-3.11.jar"))
                }
            }
        """

        expect:
        succeeds ':consumer:checkConfiguration'


        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }
    // endregion dependencies - projects

    // region dependencies - modules (GAV)
    def 'can add dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite using a GAV string'() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        dependencies {
            // production code requires commons-lang3 at runtime, which will leak into tests' runtime classpaths
            implementation("org.apache.commons:commons-lang3:3.11")
        }

        testing {
            suites {
                val test by getting(JvmTestSuite::class) {
                    dependencies {
                        implementation("com.google.guava:guava:30.1.1-jre")
                        compileOnly("javax.servlet:servlet-api:3.0-alpha-1")
                        runtimeOnly("mysql:mysql-connector-java:8.0.26")
                    }
                }
                val integTest by registering(JvmTestSuite::class) {
                    // intentionally setting lower versions of the same dependencies on the `test` suite to show that no conflict resolution should be taking place
                    dependencies {
                        implementation(project())
                        implementation("com.google.guava:guava:29.0-jre")
                        compileOnly("javax.servlet:servlet-api:2.5")
                        runtimeOnly("mysql:mysql-connector-java:6.0.6")
                    }
                }
            }
        }

        tasks.named("check") {
            dependsOn(testing.suites.named("integTest"))
        }

        tasks.register("checkConfiguration") {
            dependsOn("test", "integTest")

            val testCompileClasspathFileNames = configurations.getByName("testCompileClasspath").files.map { it.name }
            val testRuntimeClasspathFileNames = configurations.getByName("testRuntimeClasspath").files.map { it.name }
            val integTestCompileClasspathFileNames = configurations.getByName("integTestCompileClasspath").files.map { it.name }
            val integTestRuntimeClasspathFileNames = configurations.getByName("integTestRuntimeClasspath").files.map { it.name }

            doLast {

                assert(testCompileClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "servlet-api-3.0-alpha-1.jar", "guava-30.1.1-jre.jar")))
                assert(!testCompileClasspathFileNames.contains("mysql-connector-java-8.0.26.jar")) { "runtimeOnly dependency" }
                assert(testRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "guava-30.1.1-jre.jar", "mysql-connector-java-8.0.26.jar")))
                assert(!testRuntimeClasspathFileNames.contains("servlet-api-3.0-alpha-1.jar")) { "compileOnly dependency" }

                assert(integTestCompileClasspathFileNames.containsAll(listOf("servlet-api-2.5.jar", "guava-29.0-jre.jar")))
                assert(!integTestCompileClasspathFileNames.contains("commons-lang3-3.11.jar")) { "implementation dependency of project, should not leak to integTest" }
                assert(!integTestCompileClasspathFileNames.contains("mysql-connector-java-6.0.6.jar")) { "runtimeOnly dependency" }
                assert(integTestRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "guava-29.0-jre.jar", "mysql-connector-java-6.0.6.jar")))
                assert(!integTestRuntimeClasspathFileNames.contains("servlet-api-2.5.jar")) { "compileOnly dependency" }
            }
        }
        """

        expect:
        succeeds 'checkConfiguration'
    }

    def 'can add dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite via DependencyHandler using a GAV string'() {
        given:
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    val integTest by registering(JvmTestSuite::class)
                }
            }

            dependencies {
                // production code requires commons-lang3 at runtime, which will leak into tests' runtime classpaths
                implementation("org.apache.commons:commons-lang3:3.11")

                testImplementation("com.google.guava:guava:30.1.1-jre")
                testCompileOnly("javax.servlet:servlet-api:3.0-alpha-1")
                testRuntimeOnly("mysql:mysql-connector-java:8.0.26")

                // intentionally setting lower versions of the same dependencies on the `test` suite to show that no conflict resolution should be taking place
                val integTestImplementation by configurations.getting
                integTestImplementation(project)
                integTestImplementation("com.google.guava:guava:29.0-jre")
                val integTestCompileOnly by configurations.getting
                integTestCompileOnly("javax.servlet:servlet-api:2.5")
                val integTestRuntimeOnly by configurations.getting
                integTestRuntimeOnly("mysql:mysql-connector-java:6.0.6")
            }

            tasks.named("check") {
                dependsOn(testing.suites.named("integTest"))
            }

            tasks.register("checkConfiguration") {
                dependsOn("test", "integTest")

                val testCompileClasspathFileNames = configurations.getByName("testCompileClasspath").files.map { it.name }
                val testRuntimeClasspathFileNames = configurations.getByName("testRuntimeClasspath").files.map { it.name }
                val integTestCompileClasspathFileNames = configurations.getByName("integTestCompileClasspath").files.map { it.name }
                val integTestRuntimeClasspathFileNames = configurations.getByName("integTestRuntimeClasspath").files.map { it.name }

                doLast {
                    assert(testCompileClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "servlet-api-3.0-alpha-1.jar", "guava-30.1.1-jre.jar")))
                    assert(!testCompileClasspathFileNames.contains("mysql-connector-java-8.0.26.jar")) { "runtimeOnly dependency" }
                    assert(testRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "guava-30.1.1-jre.jar", "mysql-connector-java-8.0.26.jar")))
                    assert(!testRuntimeClasspathFileNames.contains("servlet-api-3.0-alpha-1.jar")) { "compileOnly dependency" }

                    assert(integTestCompileClasspathFileNames.containsAll(listOf("servlet-api-2.5.jar", "guava-29.0-jre.jar")))
                    assert(!integTestCompileClasspathFileNames.contains("commons-lang3-3.11.jar")) { "implementation dependency of project, should not leak to integTest" }
                    assert(!integTestCompileClasspathFileNames.contains("mysql-connector-java-6.0.6.jar")) { "runtimeOnly dependency" }
                    assert(integTestRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "guava-29.0-jre.jar", "mysql-connector-java-6.0.6.jar")))
                    assert(!integTestRuntimeClasspathFileNames.contains("servlet-api-2.5.jar")) { "compileOnly dependency" }
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'
    }

    def 'can add dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite via DependencyHandler using GAV named arguments'() {
        given:
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    val integTest by registering(JvmTestSuite::class)
                }
            }

            dependencies {
                // production code requires commons-lang3 at runtime, which will leak into tests' runtime classpaths
                implementation("org.apache.commons:commons-lang3:3.11")

                testImplementation(group = "com.google.guava", name = "guava", version = "30.1.1-jre")
                testCompileOnly(group = "javax.servlet", name = "servlet-api", version = "3.0-alpha-1")
                testRuntimeOnly(group = "mysql", name = "mysql-connector-java", version = "8.0.26")

                // intentionally setting lower versions of the same dependencies on the `test` suite to show that no conflict resolution should be taking place
                val integTestImplementation by configurations.getting
                integTestImplementation(project)
                integTestImplementation(group = "com.google.guava", name = "guava", version = "29.0-jre")
                val integTestCompileOnly by configurations.getting
                integTestCompileOnly(group = "javax.servlet", name = "servlet-api", version = "2.5")
                val integTestRuntimeOnly by configurations.getting
                integTestRuntimeOnly(group = "mysql", name = "mysql-connector-java", version = "6.0.6")
            }

            tasks.named("check") {
                dependsOn(testing.suites.named("integTest"))
            }

            tasks.register("checkConfiguration") {
                dependsOn("test", "integTest")

                val testCompileClasspathFileNames = configurations.getByName("testCompileClasspath").files.map { it.name }
                val testRuntimeClasspathFileNames = configurations.getByName("testRuntimeClasspath").files.map { it.name }
                val integTestCompileClasspathFileNames = configurations.getByName("integTestCompileClasspath").files.map { it.name }
                val integTestRuntimeClasspathFileNames = configurations.getByName("integTestRuntimeClasspath").files.map { it.name }

                doLast {
                    assert(testCompileClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "servlet-api-3.0-alpha-1.jar", "guava-30.1.1-jre.jar")))
                    assert(!testCompileClasspathFileNames.contains("mysql-connector-java-8.0.26.jar")) { "runtimeOnly dependency" }
                    assert(testRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "guava-30.1.1-jre.jar", "mysql-connector-java-8.0.26.jar")))
                    assert(!testRuntimeClasspathFileNames.contains("servlet-api-3.0-alpha-1.jar")) { "compileOnly dependency" }

                    assert(integTestCompileClasspathFileNames.containsAll(listOf("servlet-api-2.5.jar", "guava-29.0-jre.jar")))
                    assert(!integTestCompileClasspathFileNames.contains("commons-lang3-3.11.jar")) { "implementation dependency of project, should not leak to integTest" }
                    assert(!integTestCompileClasspathFileNames.contains("mysql-connector-java-6.0.6.jar")) { "runtimeOnly dependency" }
                    assert(integTestRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "guava-29.0-jre.jar", "mysql-connector-java-6.0.6.jar")))
                    assert(!integTestRuntimeClasspathFileNames.contains("servlet-api-2.5.jar")) { "compileOnly dependency" }
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'
    }

    def 'can add dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite via DependencyHandler using named args'() {
        given:
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    val integTest by registering(JvmTestSuite::class)
                }
            }

            dependencies {
                // production code requires commons-lang3 at runtime, which will leak into tests' runtime classpaths
                implementation("org.apache.commons:commons-lang3:3.11")

                testImplementation(group = "com.google.guava", name = "guava", version = "30.1.1-jre")
                testCompileOnly(group = "javax.servlet", name = "servlet-api", version = "3.0-alpha-1")
                testRuntimeOnly(group = "mysql", name = "mysql-connector-java", version = "8.0.26")

                // intentionally setting lower versions of the same dependencies on the `test` suite to show that no conflict resolution should be taking place
                val integTestImplementation by configurations.getting
                integTestImplementation(project)
                integTestImplementation(group = "com.google.guava", name = "guava", version = "29.0-jre")
                val integTestCompileOnly by configurations.getting
                integTestCompileOnly(group = "javax.servlet", name = "servlet-api", version = "2.5")
                val integTestRuntimeOnly by configurations.getting
                integTestRuntimeOnly(group = "mysql", name = "mysql-connector-java", version = "6.0.6")
            }

            tasks.named("check") {
                dependsOn(testing.suites.named("integTest"))
            }

            tasks.register("checkConfiguration") {
                dependsOn("test", "integTest")

                val testCompileClasspathFileNames = configurations.getByName("testCompileClasspath").files.map { it.name }
                val testRuntimeClasspathFileNames = configurations.getByName("testRuntimeClasspath").files.map { it.name }
                val integTestCompileClasspathFileNames = configurations.getByName("integTestCompileClasspath").files.map { it.name }
                val integTestRuntimeClasspathFileNames = configurations.getByName("integTestRuntimeClasspath").files.map { it.name }

                doLast {
                    assert(testCompileClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "servlet-api-3.0-alpha-1.jar", "guava-30.1.1-jre.jar")))
                    assert(!testCompileClasspathFileNames.contains("mysql-connector-java-8.0.26.jar")) { "runtimeOnly dependency" }
                    assert(testRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "guava-30.1.1-jre.jar", "mysql-connector-java-8.0.26.jar")))
                    assert(!testRuntimeClasspathFileNames.contains("servlet-api-3.0-alpha-1.jar")) { "compileOnly dependency" }

                    assert(integTestCompileClasspathFileNames.containsAll(listOf("servlet-api-2.5.jar", "guava-29.0-jre.jar")))
                    assert(!integTestCompileClasspathFileNames.contains("commons-lang3-3.11.jar")) { "implementation dependency of project, should not leak to integTest" }
                    assert(!integTestCompileClasspathFileNames.contains("mysql-connector-java-6.0.6.jar")) { "runtimeOnly dependency" }
                    assert(integTestRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "guava-29.0-jre.jar", "mysql-connector-java-6.0.6.jar")))
                    assert(!integTestRuntimeClasspathFileNames.contains("servlet-api-2.5.jar")) { "compileOnly dependency" }
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'
    }

    // region multiple GAV strings
    def "can NOT add multiple GAV dependencies to #suiteDesc in a single method call- at the top level (varargs)"() {
        given:
        buildKotlinFile << """
            plugins {
                `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}


            testing {
                suites {
                    $suiteDeclaration
                }
            }

            dependencies {
                val ${suiteName}Implementation = configurations.getByName("${suiteName}Implementation")

                // This results in a call to the named-args version: DependencyHandlerExtensions.kt#create(String, String, String, String, String, String),
                // using commons-lang as the group and guava as the name.  This causes a request for a non-existent dep with the GAV string
                // "org.apache.commons:commons-lang3:3.11:com.google.guava:guava:30.1.1-jre:" that fails to resolve at runtime.
                ${suiteName}Implementation("org.apache.commons:commons-lang3:3.11", "com.google.guava:guava:30.1.1-jre")
            }

            tasks.register("checkConfiguration") {
                dependsOn(configurations.getByName("${suiteName}CompileClasspath"))
                // Due to the named-args method being invoked, we are actually requesting a single dependency
                val ${suiteName}Implementation = configurations.getByName("${suiteName}Implementation")
                // We might get junit included too, dependending on the test suite, so filter it
                val deps = ${suiteName}Implementation.dependencies
                            .filter { it.name != "junit-jupiter" }
                            .filter { it.name != "junit-platform-launcher" }
                            .map { listOf(it.group, it.name, it.version ?: "null") }
                doLast {
                    assert(deps.size == 1) { "expected 1 dependency, found " + (deps.size) + " dependencies" }

                    // The dependency uses the 2 args we supplied incorrectly
                    val requested = deps.get(0)
                    assert(requested[0] == "org.apache.commons:commons-lang3:3.11") { "expected commons-lang3 group" }
                    assert(requested[1] == "com.google.guava:guava:30.1.1-jre") { "expected guava name" }
                    assert(requested[2] == "null") { "expected null version" }
                }
            }
        """

        file("src/$suiteName/java/org/sample/Test.java") << """
            package org.sample;

            import org.junit.Test;

            import static org.junit.Assert.assertEquals;

            public class Test {
                @Test
                public void test() {
                    assertEquals(1 + 1, 2);
                }
            }
        """

        expect: 'we will request an incorrect dependency'
        succeeds 'checkConfiguration'

        and: 'and not be able to run the suite, failing at resolution time'
        fails suiteName
        result.assertHasErrorOutput("Could not resolve all files for configuration ':${suiteName}CompileClasspath'.")
        result.assertHasErrorOutput("Could not find org.apache.commons:commons-lang3:3.11:com.google.guava:guava:30.1.1-jre:.")

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def "can NOT add multiple GAV dependencies to #suiteDesc in a single method call - at the top level (list)"() {
        given:
        buildKotlinFile << """
            plugins {
                `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}


            testing {
                suites {
                    $suiteDeclaration
                }
            }

            dependencies {
                val ${suiteName}Implementation = configurations.getByName("${suiteName}Implementation")
                ${suiteName}Implementation(listOf("org.apache.commons:commons-lang3:3.11", "com.google.guava:guava:30.1.1-jre"))
            }
        """

        expect:
        fails 'help'
        result.assertHasErrorOutput("Cannot convert the provided notation to an object of type Dependency: [org.apache.commons:commons-lang3:3.11, com.google.guava:guava:30.1.1-jre].")

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def "can NOT add multiple GAV dependencies to #suiteDesc (list)"() {
        given:
        buildKotlinFile << """
            plugins {
                `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(listOf("org.apache.commons:commons-lang3:3.11", "com.google.guava:guava:30.1.1-jre"))
                        }
                    }
                }
            }
        """


        expect:
        fails 'help'
        result.assertHasErrorOutput("None of the following functions can be called with the arguments supplied")

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }
    // endregion multiple GAV strings
    // endregion dependencies - modules (GAV)

    // region dependency constraints - modules (GAV)
    def 'can add dependency constraints to the implementation, compileOnly and runtimeOnly configurations of a suite using a GAV string'() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        dependencies {
            // production code requires commons-lang3 at runtime, which will leak into tests' runtime classpaths
            implementation("org.apache.commons:commons-lang3:3.11")
            api("com.google.guava:guava:30.1.1-jre")
        }

        testing {
            suites {
                val test by getting(JvmTestSuite::class) {
                    dependencies {
                        // Constrain commons-lang3 and guava
                        implementation(constraint("org.apache.commons:commons-lang3:3.12.0"))
                        implementation(constraint("com.google.guava:guava:33.0.0-jre"))
                    }
                }
                val integTest by registering(JvmTestSuite::class) {
                    // intentionally setting lower versions of the same dependencies on the `test` suite to show that no conflict resolution should be taking place
                    dependencies {
                        implementation(project())
                        // Should not add dependency on commons-lang3
                        implementation(constraint("org.apache.commons:commons-lang3:3.10!!"))
                        implementation(constraint("com.google.guava:guava:29.0-jre!!"))
                    }
                }
            }
        }

        tasks.named("check") {
            dependsOn(testing.suites.named("integTest"))
        }

        tasks.register("checkConfiguration") {
            dependsOn("test", "integTest")

            val testCompileClasspathFileNames = configurations.getByName("testCompileClasspath").files.map { it.name }
            val testRuntimeClasspathFileNames = configurations.getByName("testRuntimeClasspath").files.map { it.name }
            val integTestCompileClasspathFileNames = configurations.getByName("integTestCompileClasspath").files.map { it.name }
            val integTestRuntimeClasspathFileNames = configurations.getByName("integTestRuntimeClasspath").files.map { it.name }

            doLast {

                assert(testCompileClasspathFileNames.containsAll(listOf("commons-lang3-3.12.0.jar", "guava-33.0.0-jre.jar")))
                assert(testRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.12.0.jar", "guava-33.0.0-jre.jar")))

                assert(integTestCompileClasspathFileNames.containsAll(listOf("guava-29.0-jre.jar"))) { "constraints apply to compile classpath" }
                assert(!integTestCompileClasspathFileNames.contains("commons-lang3-3.11.jar")) { "implementation dependency of project, should not leak to integTest" }
                assert(!integTestCompileClasspathFileNames.contains("commons-lang3-3.10.jar")) { "constraint of integTest should not add dependency" }
                assert(integTestRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.10.jar", "guava-29.0-jre.jar"))) { "constraints apply to runtime classpath" }
            }
        }
        """

        expect:
        succeeds 'checkConfiguration'
    }
    // endregion dependency constraints - modules (GAV)

    // region dependencies - dependency objects
    def 'can add dependency objects to the implementation, compileOnly and runtimeOnly configurations of a suite'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val commonsLang = dependencies.create("org.apache.commons:commons-lang3:3.11")
            val servletApi = dependencies.create("javax.servlet:servlet-api:3.0-alpha-1")
            val mysql = dependencies.create("mysql:mysql-connector-java:8.0.26")

            testing {
                suites {
                    val test by getting(JvmTestSuite::class) {
                        dependencies {
                            implementation(commonsLang)
                            compileOnly(servletApi)
                            runtimeOnly(mysql)
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("test")

                val testCompileClasspathFileNames = configurations.getByName("testCompileClasspath").files.map { it.name }
                val testRuntimeClasspathFileNames = configurations.getByName("testRuntimeClasspath").files.map { it.name }

                doLast {
                    assert(testCompileClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "servlet-api-3.0-alpha-1.jar")))
                    assert(!testCompileClasspathFileNames.contains("mysql-connector-java-8.0.26.jar")) { "runtimeOnly dependency" }
                    assert(testRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "mysql-connector-java-8.0.26.jar")))
                    assert(!testRuntimeClasspathFileNames.contains("servlet-api-3.0-alpha-1.jar")) { "compileOnly dependency" }
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'
    }
    // endregion dependencies - dependency objects

    // region dependencies - platforms
    def "can add a platform dependency to #suiteDesc"() {
        given: "a suite that uses a platform dependency"
        settingsKotlinFile << """
            rootProject.name = "Test"

            include("platform", "consumer")
        """

        buildKotlinFile << """
            plugins {
                `java-platform`
            }

            dependencies {
                constraints {
                    api("org.apache.commons:commons-lang3:3.8.1")
                }
            }
        """

        file('platform/build.gradle.kts') << """
            plugins {
                `java-platform`
            }

            dependencies {
                constraints {
                    api("org.apache.commons:commons-lang3:3.8.1")
                }
            }
        """

        file('consumer/build.gradle.kts') << """
            plugins {
                `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        useJUnit()
                        dependencies {
                            implementation(platform(project(":platform")))
                            implementation("org.apache.commons:commons-lang3")
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
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def "can add an enforced platform dependency to #suiteDesc"() {
        given: "a suite that uses an enforced platform dependency"
        settingsKotlinFile << """
            rootProject.name = "Test"

            include("platform", "consumer")
        """

        file('platform/build.gradle.kts') << """
            plugins {
                `java-platform`
            }

            dependencies {
                constraints {
                    api("commons-beanutils:commons-beanutils:1.9.0") // depends on commons-collections 3.2.1
                }
            }
        """

        file('consumer/build.gradle.kts') << """
            plugins {
                `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        useJUnit()
                        dependencies {
                            implementation(enforcedPlatform(project(":platform")))
                            implementation("commons-collections:commons-collections:3.2.2")
                        }
                    }
                }
            }

            tasks.named("check") {
                dependsOn(testing.suites.named("$suiteName"))
            }

            tasks.register("checkConfiguration") {
                dependsOn("$suiteName")
                doLast {
                    val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }
                    val ${suiteName}RuntimeClasspathFileNames = configurations.getByName("${suiteName}RuntimeClasspath").files.map { it.name }

                    assert(${suiteName}CompileClasspathFileNames.containsAll(listOf("commons-beanutils-1.9.0.jar", "commons-collections-3.2.1.jar")))
                    assert(${suiteName}RuntimeClasspathFileNames.containsAll(listOf("commons-beanutils-1.9.0.jar", "commons-collections-3.2.1.jar")))
                    assert(!${suiteName}CompileClasspathFileNames.contains("commons-collections-3.2.2.jar"))
                    assert(!${suiteName}RuntimeClasspathFileNames.contains("commons-collections-3.2.2.jar"))
                }
            }
        """

        expect: "tests using a class from that enforcedPlatform will succeed"
        succeeds(suiteName)

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }
    // endregion dependencies - platforms

    // region dependencies - self-resolving dependencies
    def "can add gradleApi dependency to #suiteDesc"() {
        given:
        buildKotlinFile << """
            plugins {
                `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        useJUnit()
                        dependencies {
                            implementation(gradleApi())
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
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }
    // endregion dependencies - self-resolving dependencies

    // region dependencies - testFixtures
    def "can add testFixture dependency to #suiteDesc"() {
        given: "a multi-project build with a consumer project that depends on the fixtures in a util project"
        settingsKotlinFile << """
            rootProject.name = "Test"

            include("util", "consumer")
        """

        file("consumer/build.gradle.kts") << """
            plugins {
                `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        useJUnitJupiter()
                        dependencies {
                            implementation(testFixtures(project(":util")))
                        }
                    }
                }
            }
        """
        file("util/build.gradle.kts") << """
            plugins {
                `java-library`
                `java-test-fixtures`
            }
        """

        and: "containing a test which uses a fixture method"
        file("consumer/src/$suiteName/java/org/test/MyTest.java") << """
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
        succeeds( ":consumer:$suiteName")

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def "can add testFixture dependency to the same project to #suiteDesc"() {
        given: "a single-project build where a custom test suite depends on the fixtures in that project for its integration tests"
        buildKotlinFile << """
            plugins {
                `java-library`
                `java-test-fixtures`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        useJUnitJupiter()
                        dependencies {
                            implementation(testFixtures(project()))
                        }
                    }
                }
            }
        """

        and: "containing a test which uses a fixture method"
        file("src/$suiteName/java/org/test/MyTest.java") << """
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
        succeeds( suiteName)

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }
    // endregion dependencies - testFixtures
}
