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

package org.gradle.api.tasks.compile

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.internal.TextUtil
import spock.lang.Issue

import static org.junit.Assume.assumeNotNull

class JavaCompileCompatibilityIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {

    def setup() {
        file("src/main/java/Foo.java") << "public class Foo {}"
    }

    def "source compatibility convention is set and used for target compatibility convention"() {
        def jdk11 = AvailableJavaHomes.getJdk11()

        buildFile << """
            apply plugin: "java-base"

            java {
                ${extensionSource != null ? "sourceCompatibility = JavaVersion.toVersion('$extensionSource')" : ""}
            }

            sourceSets {
                custom {}
            }

            compileCustomJava {
                ${taskSource != null ? "sourceCompatibility = '$taskSource'" : ""}
                ${taskRelease != null ? "options.release = $taskRelease" : ""}
                ${taskToolchain != null ? "javaCompiler = javaToolchains.compilerFor { languageVersion = JavaLanguageVersion.of($taskToolchain) }" : ""}
            }

            compileCustomJava.doLast {
                logger.lifecycle("task.sourceCompatibility = '\$sourceCompatibility'")
                logger.lifecycle("task.targetCompatibility = '\$targetCompatibility'")
            }
        """

        file("src/custom/java/Test.java") << """public class Test { }"""

        when:
        withInstallations(jdk11).succeeds("compileCustomJava")

        then:
        executedAndNotSkipped(":compileCustomJava")
        outputContains("task.sourceCompatibility = '$sourceOut'")
        outputContains("task.targetCompatibility = '$targetOut'")
        classJavaVersion(classFile("java", "custom", "Test.class")) == JavaVersion.toVersion(targetOut)

        where:
        taskSource | taskRelease | extensionSource | taskToolchain | sourceOut            | targetOut
        "9"        | "8"         | "11"            | "11"          | "9"                  | "1.8"
        null       | "9"         | "11"            | "11"          | "9"                  | "9"
        null       | null        | "9"             | "11"          | "9"                  | "9"
        null       | null        | null            | "11"          | "11"                 | "11"
        null       | null        | null            | null          | currentJavaVersion() | currentJavaVersion()
    }

    def "target compatibility convention is set"() {
        def jdk11 = AvailableJavaHomes.getJdk11()

        buildFile << """
            apply plugin: "java-base"

            java {
                ${extensionTarget != null ? "targetCompatibility = JavaVersion.toVersion(${extensionTarget})" : ""}
            }

            sourceSets {
                custom {}
            }

            compileCustomJava {
                ${taskTarget != null ? "targetCompatibility = '${taskTarget}'" : ""}
                ${taskRelease ? "options.release = ${taskRelease}" : ""}
                ${taskSource ? "sourceCompatibility = '${taskSource}'" : ""}
                ${taskToolchain != null ? "javaCompiler = javaToolchains.compilerFor { languageVersion = JavaLanguageVersion.of(${taskToolchain}) }" : ""}
            }

            compileCustomJava.doLast {
                logger.lifecycle("task.sourceCompatibility = \$sourceCompatibility")
                logger.lifecycle("task.targetCompatibility = \$targetCompatibility")
            }
        """

        file("src/custom/java/Test.java") << """public class Test { }"""

        when:
        withInstallations(jdk11).succeeds("compileCustomJava")

        then:
        executedAndNotSkipped(":compileCustomJava")
        outputContains("task.sourceCompatibility = $sourceOut")
        outputContains("task.targetCompatibility = $targetOut")
        classJavaVersion(classFile("java", "custom", "Test.class")) == JavaVersion.toVersion(bytecodeOut)

        where:
        taskTarget | taskRelease | extensionTarget | taskSource | taskToolchain | sourceOut            | targetOut            | bytecodeOut
        "9"        | "8"         | "11"            | "11"       | "11"          | "11"                 | "9"                  | "1.8"
        "9"        | null        | "11"            | "8"        | "11"          | "8"                  | "9"                  | "9"
        null       | "9"         | "11"            | "11"       | "11"          | "11"                 | "9"                  | "9"
        null       | null        | "9"             | "8"        | "11"          | "8"                  | "9"                  | "9"
        null       | null        | null            | "9"        | "11"          | "9"                  | "9"                  | "9"
        null       | null        | null            | null       | "11"          | "11"                 | "11"                 | "11"
        null       | null        | null            | null       | null          | currentJavaVersion() | currentJavaVersion() | currentJavaVersion()
    }

    @Issue("https://github.com/gradle/gradle/issues/22397")
    def "uses source and target compatibility from toolchain defined by forkOptions #forkOption"() {
        def currentJdk = Jvm.current()
        def earlierJdk = AvailableJavaHomes.getDifferentVersion { it.languageVersion < currentJdk.javaVersion }
        assumeNotNull(earlierJdk)

        def path = TextUtil.normaliseFileSeparators(earlierJdk.javaHome.absolutePath.toString() + appendPath)

        buildFile << """
            apply plugin: "java"

            compileJava {
                options.fork = true
                ${configure.replace("<path>", path)}
            }

            compileJava.doLast {
                println "sourceCompatibility: '\${sourceCompatibility}'"
                println "targetCompatibility: '\${targetCompatibility}'"
            }
        """

        when:
        withInstallations(earlierJdk).run(":compileJava", "--info")

        then:
        executedAndNotSkipped(":compileJava")
        outputContains("Compiling with toolchain '${earlierJdk.javaHome.absolutePath}'")
        outputContains("sourceCompatibility: '${earlierJdk.javaVersion}'")
        outputContains("targetCompatibility: '${earlierJdk.javaVersion}'")
        classJavaVersion(javaClassFile("Foo.class")) == earlierJdk.javaVersion

        where:
        forkOption   | configure                                       | appendPath
        "java home"  | 'options.forkOptions.javaHome = file("<path>")' | ''
        "executable" | 'options.forkOptions.executable = "<path>"'     | OperatingSystem.current().getExecutableName('/bin/javac')
    }

    def "uses matching compatibility options for source and target level"() {
        def jdk = AvailableJavaHomes.getJdk(JavaVersion.VERSION_11)
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(11)
                }
            }
        """

        file("src/main/java/Bar.java") << """
            public class Bar {
                public void bar() {
                    java.util.function.Function<String, String> append = (var string) -> string + " ";
                }
            }
        """

        when:
        withInstallations(jdk).run(":compileJava", "--info")

        then:
        outputContains("Compiling with toolchain '${jdk.javaHome.absolutePath}'.")
        classJavaVersion(javaClassFile("Foo.class")) == jdk.javaVersion
    }

    def "source and target compatibility override toolchain (source #source, target #target)"() {
        def jdk11 = AvailableJavaHomes.getJdk(JavaVersion.VERSION_11)

        buildFile << """
            apply plugin: 'java'

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(11)
                }
            }

            compileJava {
                ${source != 'none' ? "sourceCompatibility = JavaVersion.toVersion($source)" : ''}
                ${target != 'none' ? "targetCompatibility = JavaVersion.toVersion($target)" : ''}
            }

            compileJava.doLast {
                logger.lifecycle("project.sourceCompatibility = '\${project.java.sourceCompatibility}'")
                logger.lifecycle("project.targetCompatibility = '\${project.java.targetCompatibility}'")
                logger.lifecycle("task.sourceCompatibility = '\$sourceCompatibility'")
                logger.lifecycle("task.targetCompatibility = '\$targetCompatibility'")
            }
        """

        when:
        withInstallations(jdk11).run(":compileJava")

        then:
        outputContains("project.sourceCompatibility = '11'")
        outputContains("project.targetCompatibility = '11'")
        outputContains("task.sourceCompatibility = '$sourceOut'")
        outputContains("task.targetCompatibility = '$targetOut'")
        classJavaVersion(javaClassFile("Foo.class")) == JavaVersion.toVersion(targetOut)

        where:
        source | target | sourceOut | targetOut
        '9'    | '10'   | '9'       | '10'
        '9'    | 'none' | '9'       | '9'
        'none' | 'none' | '11'      | '11'
    }

    def "configuring toolchain on java extension with source and target compatibility is supported"() {
        def jdk = Jvm.current()
        def prevJavaVersion = JavaVersion.toVersion(jdk.javaVersion.majorVersion.toInteger() - 1)
        buildFile << """
            apply plugin: 'java'

            java {
                sourceCompatibility = JavaVersion.toVersion('$prevJavaVersion')
                targetCompatibility = JavaVersion.toVersion('$prevJavaVersion')
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }

            compileJava.doLast {
                logger.lifecycle("project.sourceCompatibility = '\${project.java.sourceCompatibility}'")
                logger.lifecycle("project.targetCompatibility = '\${project.java.targetCompatibility}'")
                logger.lifecycle("task.sourceCompatibility = '\$sourceCompatibility'")
                logger.lifecycle("task.targetCompatibility = '\$targetCompatibility'")
            }
        """

        when:
        withInstallations(jdk).run(":compileJava")

        then:
        outputContains("project.sourceCompatibility = '$prevJavaVersion'")
        outputContains("project.targetCompatibility = '$prevJavaVersion'")
        outputContains("task.sourceCompatibility = '$prevJavaVersion'")
        outputContains("task.targetCompatibility = '$prevJavaVersion'")
        classJavaVersion(javaClassFile("Foo.class")) == JavaVersion.toVersion(prevJavaVersion)
    }

    def "configuring toolchain on java extension and clearing source and target compatibility is supported"() {
        def jdk = Jvm.current()
        def javaVersion = jdk.javaVersion

        buildFile << """
            apply plugin: 'java'

            java {
                sourceCompatibility = JavaVersion.VERSION_14
                targetCompatibility = JavaVersion.VERSION_14
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${javaVersion.majorVersion})
                }
                sourceCompatibility = null
                targetCompatibility = null
            }

            compileJava.doLast {
                logger.lifecycle("project.sourceCompatibility = '\${project.java.sourceCompatibility}'")
                logger.lifecycle("project.targetCompatibility = '\${project.java.targetCompatibility}'")
                logger.lifecycle("task.sourceCompatibility = '\$sourceCompatibility'")
                logger.lifecycle("task.targetCompatibility = '\$targetCompatibility'")
            }
        """

        when:
        withInstallations(jdk).run(":compileJava")

        then:
        outputContains("project.sourceCompatibility = '$javaVersion'")
        outputContains("project.targetCompatibility = '$javaVersion'")
        outputContains("task.sourceCompatibility = '$javaVersion'")
        outputContains("task.targetCompatibility = '$javaVersion'")
        classJavaVersion(javaClassFile("Foo.class")) == javaVersion
    }

    private static String currentJavaVersion() {
        return Jvm.current().javaVersion.toString()
    }
}
