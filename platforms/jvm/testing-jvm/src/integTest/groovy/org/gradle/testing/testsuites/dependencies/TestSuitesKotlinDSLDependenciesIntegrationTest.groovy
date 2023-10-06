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
    private versionCatalog = file('gradle', 'libs.versions.toml')

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

    def 'can add dependencies to other projects with actions (using exclude) to #suiteDesc'() {
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
                            implementation(project(":util")) {
                                exclude(group = "org.apache.commons", module = "commons-lang3")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                val compileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }

                doLast {
                    assert(!compileClasspathFileNames.contains("commons-lang3-3.11.jar"))
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

    def 'can add dependencies to other projects with actions (using because) to #suiteDesc'() {
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
                            implementation(project(":util")) {
                                because("for testing purposes")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                val reasons = configurations.getByName("${suiteName}CompileClasspath").getAllDependencies().withType(ProjectDependency::class).map { it.getReason() }
                doLast {
                    assert(reasons.size == 1)
                    reasons.forEach {
                        assert(it == "for testing purposes")
                    }
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

    def 'can add dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite using GAV named parameters'() {
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
                        implementation(module(group = "com.google.guava", name = "guava", version = "30.1.1-jre"))
                        compileOnly(module(group = "javax.servlet", name = "servlet-api", version = "3.0-alpha-1"))
                        runtimeOnly(module(group = "mysql", name = "mysql-connector-java", version = "8.0.26"))
                    }
                }
                val integTest by registering(JvmTestSuite::class) {
                    // intentionally setting lower versions of the same dependencies on the `test` suite to show that no conflict resolution should be taking place
                    dependencies {
                        implementation(project())
                        implementation(module(group = "com.google.guava", name = "guava", version = "29.0-jre"))
                        compileOnly(module(group = "javax.servlet", name = "servlet-api", version = "2.5"))
                        runtimeOnly(module(group = "mysql", name = "mysql-connector-java", version = "6.0.6"))
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

    def "can add dependency with actions on suite using a #desc"() {
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
                            implementation($dependencyNotation) {
                                exclude(mapOf("group" to "commons-collections", "module" to "commons-collections"))
                                exclude(group = "commons-collections", module = "commons-collections")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                val testCompileClasspathFileNames = configurations.getByName("testCompileClasspath").files.map { it.name }
                doLast {
                    assert(testCompileClasspathFileNames.contains("commons-beanutils-1.9.4.jar"))
                    assert(!testCompileClasspathFileNames.contains("commons-collections-3.2.2.jar")) { "excluded dependency" }
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        desc                  | dependencyNotation
        'GAV string'          | '"commons-beanutils:commons-beanutils:1.9.4"'
        'GAV named arguments' | 'module(group = "commons-beanutils", name = "commons-beanutils", version = "1.9.4")'
    }

    def "can add dependencies using a non-String CharSequence: #type"() {
        given:
        buildKotlinFile << """
        import org.apache.commons.lang3.text.StrBuilder;

        buildscript {
            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            dependencies {
                classpath("org.apache.commons:commons-lang3:3.11")
            }
        }

        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        val buf: $type = $creationNotation

        testing {
            suites {
                val test by getting(JvmTestSuite::class) {
                    dependencies {
                        implementation(buf)
                    }
                }
            }
        }

        tasks.register("checkConfiguration") {
            dependsOn("test")

            val testCompileClasspathFileNames = configurations.getByName("testCompileClasspath").files.map { it.name }
            doLast {
                assert(testCompileClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar")))
            }
        }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        type            | creationNotation
        'StringBuilder' | "StringBuilder(\"org.apache.commons:commons-lang3:3.11\")"
        'StrBuilder'    | "StrBuilder(\"org.apache.commons:commons-lang3:3.11\")"
    }

    // region multiple GAV strings
    def "can NOT add multiple GAV dependencies to #suiteDesc - at the top level (varargs)"() {
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

    def "can NOT add multiple GAV dependencies to #suiteDesc - at the top level (list)"() {
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

    def 'can add dependency objects with actions (using exclude) to #suiteDesc'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val beanUtils = dependencyFactory.create("commons-beanutils:commons-beanutils:1.9.4")

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                exclude(group = "commons-collections", module = "commons-collections")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("$suiteName")
                val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }
                doLast {
                    assert(${suiteName}CompileClasspathFileNames.contains("commons-beanutils-1.9.4.jar"))
                    assert(!${suiteName}CompileClasspathFileNames.contains("commons-collections-3.2.2.jar")) { "excluded dependency" }
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can add dependency objects with actions (using exclude) to #suiteDesc - with smart cast'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val beanUtils = dependencies.create("commons-beanutils:commons-beanutils:1.9.4")

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                this as ExternalModuleDependency
                                exclude(group = "commons-collections", module = "commons-collections")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("$suiteName")

                val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }
                doLast {
                    assert(${suiteName}CompileClasspathFileNames.contains("commons-beanutils-1.9.4.jar"))
                    assert(!${suiteName}CompileClasspathFileNames.contains("commons-collections-3.2.2.jar")) { "excluded dependency" }
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can add dependency objects with actions (using because) to #suiteDesc'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val beanUtils = dependencies.create("commons-beanutils:commons-beanutils:1.9.4")

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                because("for testing purposes")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("$suiteName")

                val reasons = configurations.getByName("${suiteName}CompileClasspath").getAllDependencies().withType(ModuleDependency::class).matching { it.group.equals("commons-beanutils") }.map { it.getReason() }
                doLast {
                    assert(reasons.size == 1)
                    reasons.forEach {
                        assert(it == "for testing purposes")
                    }
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }
    // endregion dependencies - dependency objects

    // region dependencies - dependency providers
    def 'can add dependency providers which provide dependency objects to the implementation, compileOnly and runtimeOnly configurations of a suite'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val commonsLang = project.provider { dependencies.create("org.apache.commons:commons-lang3:3.11") }
            val servletApi = project.provider { dependencies.create("javax.servlet:servlet-api:3.0-alpha-1") }
            val mysql = project.provider { dependencies.create("mysql:mysql-connector-java:8.0.26") }

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

    def 'can NOT add dependency providers which provide GAVs to the implementation, compileOnly and runtimeOnly configurations of a suite'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val commonsLang = project.provider { "org.apache.commons:commons-lang3:3.11" }
            val servletApi = project.provider { "javax.servlet:servlet-api:3.0-alpha-1" }
            val mysql = project.provider { "mysql:mysql-connector-java:8.0.26" }

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

        when:
        fails 'checkConfiguration'

        then:
        result.assertHasErrorOutput("None of the following functions can be called with the arguments supplied")
    }

    def 'can add dependency providers which provide dependency objects with actions (using exclude) to #suiteDesc'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val beanUtils = project.provider { dependencyFactory.create("commons-beanutils:commons-beanutils:1.9.4") }

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                exclude(group = "commons-collections", module = "commons-collections")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("$suiteName")

                val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }
                doLast {
                    assert(${suiteName}CompileClasspathFileNames.contains("commons-beanutils-1.9.4.jar"))
                    assert(!${suiteName}CompileClasspathFileNames.contains("commons-collections-3.2.2.jar")) { "excluded dependency" }
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can add dependency providers which provide dependency objects with actions (using because) to #suiteDesc - with smart cast'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val beanUtils = project.provider { dependencies.create("commons-beanutils:commons-beanutils:1.9.4") }

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                this as ExternalModuleDependency
                                because("for testing purposes")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("$suiteName")

                val reasons = configurations.getByName("${suiteName}CompileClasspath").getAllDependencies().withType(ModuleDependency::class).matching { it.group.equals("commons-beanutils") }.map { it.getReason() }
                doLast {
                    assert(reasons.size == 1)
                    reasons.forEach {
                        assert(it == "for testing purposes")
                    }
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can NOT add dependency providers which provide GAVs with actions (using excludes) to #suiteDesc'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val beanUtils = project.provider { "commons-beanutils:commons-beanutils:1.9.4" }

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                exclude(group = "commons-collections", module = "commons-collections")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("$suiteName")
                doLast {
                    val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }

                    assert(${suiteName}CompileClasspathFileNames.contains("commons-beanutils-1.9.4.jar"))
                    assert(!${suiteName}CompileClasspathFileNames.contains("commons-collections-3.2.2.jar")) { "excluded dependency" }
                }
            }
        """

        when:
        fails 'checkConfiguration'

        then:
        result.assertHasErrorOutput("None of the following functions can be called with the arguments supplied")

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can NOT add dependency providers which provide GAVs with actions (using excludes) to #suiteDesc - with smart cast'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val beanUtils = project.provider { "commons-beanutils:commons-beanutils:1.9.4" }

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                this as ExternalModuleDependency
                                exclude(group = "commons-collections", module = "commons-collections")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("$suiteName")

                val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }
                doLast {
                    assert(${suiteName}CompileClasspathFileNames.contains("commons-beanutils-1.9.4.jar"))
                    assert(!${suiteName}CompileClasspathFileNames.contains("commons-collections-3.2.2.jar")) { "excluded dependency" }
                }
            }
        """

        when:
        fails 'checkConfiguration'

        then:
        result.assertHasErrorOutput("None of the following functions can be called with the arguments supplied")

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can NOT add dependency providers which provide GAVs with actions (using because) to #suiteDesc'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val beanUtils = project.provider { "commons-beanutils:commons-beanutils:1.9.4" }

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                because("for testing purposes")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("$suiteName")

                val reasons = configurations.getByName("${suiteName}CompileClasspath").getAllDependencies().withType(ModuleDependency::class).matching { it.group.equals("commons-beanutils") }.map { it.getReason() }
                doLast {
                    assert(reasons.size == 1)
                    reasons.forEach {
                        assert(it == "for testing purposes")
                    }
                }
            }
        """

        when:
        fails 'checkConfiguration'

        then:
        result.assertHasErrorOutput("None of the following functions can be called with the arguments supplied")

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }
    // endregion dependencies - dependency providers

    // region dependencies - Version Catalog
    def 'can add dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite via a Version Catalog'() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        testing {
            suites {
                val integTest by registering(JvmTestSuite::class) {
                    dependencies {
                        implementation(libs.guava)
                        compileOnly(libs.commons.lang3)
                        runtimeOnly(libs.mysql.connector)
                    }
                }
            }
        }

        tasks.named("check") {
            dependsOn(testing.suites.named("integTest"))
        }

        tasks.register("checkConfiguration") {
            dependsOn("test", "integTest")

            val integTestCompileClasspathFileNames = configurations.getByName("integTestCompileClasspath").files.map { it.name }
            val integTestRuntimeClasspathFileNames = configurations.getByName("integTestRuntimeClasspath").files.map { it.name }
            doLast {
                assert(integTestCompileClasspathFileNames.containsAll(listOf("guava-30.1.1-jre.jar")))
                assert(integTestRuntimeClasspathFileNames.containsAll(listOf("guava-30.1.1-jre.jar")))
                assert(integTestCompileClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar")))
                assert(!integTestRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar")))
                assert(!integTestCompileClasspathFileNames.containsAll(listOf("mysql-connector-java-6.0.6.jar")))
                assert(integTestRuntimeClasspathFileNames.containsAll(listOf("mysql-connector-java-6.0.6.jar")))
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
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        testing {
            suites {
                $suiteDeclaration {
                    dependencies {
                        implementation(libs.commons.beanutils) {
                            exclude(group = "commons-collections", module = "commons-collections")
                        }
                    }
                }
            }
        }

        tasks.register("checkConfiguration") {
            dependsOn("$suiteName")
            val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }
            doLast {
                assert(${suiteName}CompileClasspathFileNames.contains("commons-beanutils-1.9.4.jar"))
                assert(!${suiteName}CompileClasspathFileNames.contains("commons-collections-3.2.2.jar")) { "excluded dependency" }
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
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can add dependencies via a Version Catalog with actions (using exclude) to #suiteDesc - with smart cast'() {
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
                        implementation(libs.commons.beanutils) {
                            this as ExternalModuleDependency
                            exclude(group = "commons-collections", module = "commons-collections")
                        }
                    }
                }
            }
        }

        tasks.register("checkConfiguration") {
            dependsOn("$suiteName")

            val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }
            doLast {
                assert(${suiteName}CompileClasspathFileNames.contains("commons-beanutils-1.9.4.jar"))
                assert(!${suiteName}CompileClasspathFileNames.contains("commons-collections-3.2.2.jar")) { "excluded dependency" }
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
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can add dependencies via a Version Catalog with actions (using because) to #suiteDesc'() {
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
                        implementation(libs.commons.beanutils) {
                            because("for testing purposes")
                        }
                    }
                }
            }
        }

        tasks.register("checkConfiguration") {
            dependsOn("$suiteName")

            val reasons = configurations.getByName("${suiteName}CompileClasspath").getAllDependencies().withType(ModuleDependency::class).matching { it.group.equals("commons-beanutils") }.map { it.getReason() }
            doLast {
                assert(reasons.size == 1)
                reasons.forEach {
                    assert(it == "for testing purposes")
                }
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
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can add dependencies using a Version Catalog bundle to #suiteDesc'() {
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
                        implementation.bundle(libs.bundles.groovy)
                    }
                }
            }
        }

        tasks.named("check") {
            dependsOn(testing.suites.named("$suiteName"))
        }

        tasks.register("checkConfiguration") {
            dependsOn("$suiteName")

            val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }
            val ${suiteName}RuntimeClasspathFileNames = configurations.getByName("${suiteName}RuntimeClasspath").files.map { it.name }
            doLast {
                assert(${suiteName}CompileClasspathFileNames.containsAll(listOf("groovy-json-3.0.5.jar", "groovy-nio-3.0.5.jar", "groovy-3.0.5.jar")))
                assert(${suiteName}RuntimeClasspathFileNames.containsAll(listOf("groovy-json-3.0.5.jar", "groovy-nio-3.0.5.jar", "groovy-3.0.5.jar")))
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
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can add dependencies using a Version Catalog with a hierarchy of aliases to #suiteDesc'() {
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
                        implementation(libs.commons)
                        implementation(libs.commons.collections)
                        runtimeOnly(libs.commons.io)
                        runtimeOnly(libs.commons.io.csv)
                    }
                }
            }
        }

        tasks.named("check") {
            dependsOn(testing.suites.named("$suiteName"))
        }

        tasks.register("checkConfiguration") {
            dependsOn("$suiteName")

            val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }
            val ${suiteName}RuntimeClasspathFileNames = configurations.getByName("${suiteName}RuntimeClasspath").files.map { it.name }
            doLast {
                assert(${suiteName}CompileClasspathFileNames.containsAll(listOf("commons-lang3-3.12.0.jar", "commons-collections4-4.4.jar")))
                assert(${suiteName}RuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.12.0.jar", "commons-collections4-4.4.jar", "commons-io-2.11.0.jar", "commons-csv-1.9.0.jar")))
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
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can add dependencies using a Version Catalog defined programmatically to #suiteDesc'() {
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
                        implementation(libs.guava)
                        compileOnly(libs.commons.lang3)
                        runtimeOnly(libs.mysql.connector)
                    }
                }
            }
        }

        tasks.named("check") {
            dependsOn(testing.suites.named("$suiteName"))
        }

        tasks.register("checkConfiguration") {
            dependsOn("$suiteName")

            val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }
            val ${suiteName}RuntimeClasspathFileNames = configurations.getByName("${suiteName}RuntimeClasspath").files.map { it.name }
            doLast {
                assert(${suiteName}CompileClasspathFileNames.containsAll(listOf("guava-30.1.1-jre.jar")))
                assert(${suiteName}RuntimeClasspathFileNames.containsAll(listOf("guava-30.1.1-jre.jar")))
                assert(${suiteName}CompileClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar")))
                assert(!${suiteName}RuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar")))
                assert(!${suiteName}CompileClasspathFileNames.containsAll(listOf("mysql-connector-java-6.0.6.jar")))
                assert(${suiteName}RuntimeClasspathFileNames.containsAll(listOf("mysql-connector-java-6.0.6.jar")))
            }
        }
        """

        settingsKotlinFile <<"""
        dependencyResolutionManagement {
            versionCatalogs {
                create("libs") {
                    version("guava", "30.1.1-jre")
                    version("commons-lang3", "3.11")
                    version("mysql-connector", "6.0.6")

                    library("guava", "com.google.guava", "guava").versionRef("guava")
                    library("commons-lang3", "org.apache.commons", "commons-lang3").versionRef("commons-lang3")
                    library("mysql-connector", "mysql", "mysql-connector-java").versionRef("mysql-connector")
                }
            }
        }
        """.stripIndent(8)

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }
    // endregion dependencies - Version Catalog

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

    // region dependencies - platforms + version catalog

    def 'can add platform dependencies to configurations of a suite via a Version Catalog'() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        testing {
            suites {
                val integTest by registering(JvmTestSuite::class) {
                    dependencies {
                        implementation(platform(libs.junit.bom))
                        implementation(libs.junit.jupiter.api)
                    }
                }
            }
        }

        tasks.named("check") {
            dependsOn(testing.suites.named("integTest"))
        }

        tasks.register("checkConfiguration") {
            dependsOn("test", "integTest")

            val integTestCompileClasspathFileNames = configurations.getByName("integTestCompileClasspath").files.map { it.name }
            doLast {
                assert(integTestCompileClasspathFileNames.containsAll(listOf("junit-jupiter-api-5.10.0.jar")))
            }
        }
        """

        versionCatalog = file('gradle', 'libs.versions.toml') << """
        [libraries]
        junit-bom = "org.junit:junit-bom:5.10.0"
        junit-jupiter-api.module = "org.junit.jupiter:junit-jupiter-api"
        """.stripIndent(8)

        expect:
        succeeds 'checkConfiguration'
    }

    def 'can add enforced platform dependencies to configurations of a suite via a Version Catalog'() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        testing {
            suites {
                val integTest by registering(JvmTestSuite::class) {
                    dependencies {
                        implementation(enforcedPlatform(libs.junit.bom))
                        implementation(libs.junit.jupiter.api)
                    }
                }
            }
        }

        tasks.named("check") {
            dependsOn(testing.suites.named("integTest"))
        }

        tasks.register("checkConfiguration") {
            dependsOn("test", "integTest")

            val integTestCompileClasspathFileNames = configurations.getByName("integTestCompileClasspath").files.map { it.name }
            doLast {
                assert(integTestCompileClasspathFileNames.containsAll(listOf("junit-jupiter-api-5.10.0.jar")))
            }
        }
        """

        versionCatalog = file('gradle', 'libs.versions.toml') << """
        [libraries]
        junit-bom = "org.junit:junit-bom:5.10.0"
        junit-jupiter-api.module = "org.junit.jupiter:junit-jupiter-api"
        """.stripIndent(8)

        expect:
        succeeds 'checkConfiguration'
    }

    // endregion dependencies - platforms + version catalog

    // region dependencies - file collections
    def "can add file collection dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite"() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        testing {
            suites {
                val test by getting(JvmTestSuite::class) {
                    dependencies {
                        implementation(files("libs/dummy-1.jar"))
                        compileOnly(files("libs/dummy-2.jar"))
                        runtimeOnly(files("libs/dummy-3.jar"))
                    }
                }
            }
        }

        tasks.register("checkConfiguration") {
            dependsOn("test")

            val testCompileClasspathFileNames = configurations.getByName("testCompileClasspath").files.map { it.name }
            val testRuntimeClasspathFileNames = configurations.getByName("testRuntimeClasspath").files.map { it.name }
            doLast {
                assert(testCompileClasspathFileNames.containsAll(listOf("dummy-1.jar")))
                assert(testRuntimeClasspathFileNames.containsAll(listOf("dummy-1.jar")))
                assert(testCompileClasspathFileNames.containsAll(listOf("dummy-2.jar")))
                assert(!testRuntimeClasspathFileNames.containsAll(listOf("dummy-2.jar")))
                assert(!testCompileClasspathFileNames.containsAll(listOf("dummy-3.jar")))
                assert(testRuntimeClasspathFileNames.containsAll(listOf("dummy-3.jar")))
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
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(fileTree("libs").matching {
                                include("dummy-*.jar")
                            })
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("$suiteName")

                val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }
                doLast {
                    assert(${suiteName}CompileClasspathFileNames.containsAll(listOf("dummy-1.jar", "dummy-2.jar", "dummy-3.jar")))
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
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def "can add file collection dependencies #suiteDesc with actions"() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        val configurationActions: MutableList<String> = mutableListOf()

        testing {
            suites {
                $suiteDeclaration {
                    dependencies {
                        implementation(files("libs/dummy-1.jar", "libs/dummy-2.jar")) {
                            configurationActions.add("configured files")
                        }
                    }
                }
            }
        }

        tasks.register("checkConfiguration") {
            dependsOn("$suiteName")

            // Assert at configuration time to avoid configuration cache issues
            assert(configurationActions.containsAll(listOf("configured files")))

            val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }
            doLast {
                assert(${suiteName}CompileClasspathFileNames.containsAll(listOf("dummy-1.jar", "dummy-2.jar")))
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
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }
    // endregion dependencies - file collections

    // region dependencies - self-resolving dependencies
    def "can add localGroovy dependency to #suiteDesc"() {
        given:
        buildKotlinFile << """
            plugins {
                `groovy`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        useJUnit()
                        dependencies {
                            implementation(localGroovy())
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
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

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

    def "can add gradleTestKit dependency to #suiteDesc"() {
        given:
        buildKotlinFile << """
            plugins {
                `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        useJUnitJupiter()
                        dependencies {
                            implementation(gradleTestKit())
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
