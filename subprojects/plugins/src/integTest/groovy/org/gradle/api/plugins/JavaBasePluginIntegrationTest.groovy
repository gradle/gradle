/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.util.internal.TextUtil

class JavaBasePluginIntegrationTest extends AbstractIntegrationSpec {

    def "can define and build a source set with implementation dependencies"() {
        settingsFile << """
            include 'main', 'tests'
        """
        buildFile << """
            project(':main') {
                apply plugin: 'java'
            }
            project(':tests') {
                apply plugin: 'java-base'
                sourceSets {
                    unitTest {
                    }
                }
                dependencies {
                    unitTestImplementation project(':main')
                }
            }
        """
        file("main/src/main/java/Main.java") << """public class Main { }"""
        file("tests/src/unitTest/java/Test.java") << """public class Test { Main main = null; }"""

        expect:
        succeeds(":test:unitTestClasses")
        file("main/build/classes/java/main").assertHasDescendants("Main.class")
        file("tests/build/classes/java/unitTest").assertHasDescendants("Test.class")
    }

    def "can configure source and target Java versions"() {
        def jdk = AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_8)
        buildFile << """
            apply plugin: 'java-base'
            java {
                sourceCompatibility = JavaVersion.VERSION_1_7
                targetCompatibility = JavaVersion.VERSION_1_8
            }
            sourceSets {
                unitTest { }
            }
            compileUnitTestJava {
                options.fork = true
                options.forkOptions.javaHome = file("${TextUtil.normaliseFileSeparators(jdk.javaHome.toString())}")
            }
            compileUnitTestJava.doFirst {
                assert sourceCompatibility == "1.7"
                assert targetCompatibility == "1.8"
            }
        """
        file("src/unitTest/java/Test.java") << """public class Test { }"""

        expect:
        succeeds("unitTestClasses")
    }

    def "source compatibility convention is set and used for target compatibility convention"() {
        def jdk11 = AvailableJavaHomes.getJdk11()

        buildFile << """
            apply plugin: 'java-base'

            java {
                ${extensionSource != null ? "sourceCompatibility = JavaVersion.toVersion('$extensionSource')" : ""}
            }

            sourceSets {
                customCompile { }
            }

            compileCustomCompileJava {
                ${taskSource != null ? "sourceCompatibility = '$taskSource'" : ""}
                ${taskRelease != null ? "options.release = $taskRelease" : ""}
                ${taskToolchain != null ? "javaCompiler = javaToolchains.compilerFor { languageVersion = JavaLanguageVersion.of($taskToolchain) }" : ""}
            }

            compileCustomCompileJava.doFirst {
                assert sourceCompatibility == "${sourceOut}"
                assert targetCompatibility == "${targetOut}"
            }
        """

        file("src/customCompile/java/Test.java") << """public class Test { }"""

        expect:
        withInstallations(jdk11).succeeds("customCompileClasses")

        where:
        taskSource | taskRelease | extensionSource | taskToolchain | sourceOut            | targetOut
        "9"        | "11"        | "11"            | "11"          | "9"                  | "11" // target differs because release is set
        null       | "9"         | "11"            | "11"          | "9"                  | "9"
        null       | null        | "9"             | "11"          | "9"                  | "9"
        null       | null        | null            | "11"          | "11"                 | "11"
        null       | null        | null            | null          | currentJavaVersion() | currentJavaVersion()
    }

    def "target compatibility convention is set"() {
        def jdk11 = AvailableJavaHomes.getJdk11()

        buildFile << """
            apply plugin: 'java-base'

            java {
                ${extensionTarget != null ? "targetCompatibility = JavaVersion.toVersion(${extensionTarget})" : ""}
            }

            sourceSets {
                customCompile { }
            }

            compileCustomCompileJava {
                ${taskTarget != null ? "targetCompatibility = '${taskTarget}'" : ""}
                ${taskRelease ? "options.release = ${taskRelease}" : ""}
                ${taskSource ? "sourceCompatibility = '${taskSource}'" : ""}
                ${taskToolchain != null ? "javaCompiler = javaToolchains.compilerFor { languageVersion = JavaLanguageVersion.of(${taskToolchain}) }" : ""}
            }

            compileCustomCompileJava.doFirst {
                assert targetCompatibility == "${targetOut}"
                assert sourceCompatibility == "${sourceOut}"
            }
        """

        file("src/customCompile/java/Test.java") << """public class Test { }"""

        expect:
        withInstallations(jdk11).succeeds("customCompileClasses")

        where:
        taskTarget | taskRelease | extensionTarget | taskSource | taskToolchain | targetOut            | sourceOut
        "9"        | "11"        | "11"            | "11"       | "11"          | "9"                  | "11"
        null       | "9"         | "11"            | "11"       | "11"          | "9"                  | "11"
        null       | null        | "9"             | "8"        | "11"          | "9"                  | "8"
        null       | null        | null            | "9"        | "11"          | "9"                  | "9"
        null       | null        | null            | null       | "11"          | "11"                 | "11"
        null       | null        | null            | null       | null          | currentJavaVersion() | currentJavaVersion()
    }

    private withInstallations(Jvm... jvm) {
        def installationPaths = jvm.collect { it.javaHome.absolutePath }.join(",")
        executer
            .withArgument("-Porg.gradle.java.installations.paths=" + installationPaths)
        this
    }

    private static String currentJavaVersion() {
        return Jvm.current().javaVersion.toString()
    }
}
