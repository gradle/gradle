/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.integtests.fixtures.AbstractPluginIntegrationTest
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.jvm.Jvm
import org.gradle.util.Requires
import spock.lang.IgnoreIf
import spock.lang.Unroll

class JavaCompileToolchainIntegrationTest extends AbstractPluginIntegrationTest {

    @Unroll
    @IgnoreIf({ AvailableJavaHomes.differentJdk == null })
    def "can manually set java compiler via #type toolchain on java compile task"() {
        buildFile << """
            apply plugin: "java"

            compileJava {
                javaCompiler = javaToolchains.compilerFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        runWithToolchainConfigured(jdk)

        then:
        outputContains("Compiling with toolchain '${jdk.javaHome.absolutePath}'.")
        javaClassFile("Foo.class").exists()

        where:
        type           | jdk
        'differentJdk' | AvailableJavaHomes.getDifferentJdk()
        'current'      | Jvm.current()
    }

    @Requires(adhoc = { AvailableJavaHomes.getJdk(JavaVersion.VERSION_14) != null })
    def "can set explicit toolchain used by JavaCompile"() {
        def someJdk = AvailableJavaHomes.getJdk(JavaVersion.VERSION_14)
        buildFile << """
            apply plugin: "java"

            compileJava {
                // Make sure declaration ordering is not an issue
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${someJdk.javaVersion.majorVersion})
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        runWithToolchainConfigured(someJdk)

        then:
        outputDoesNotContain("Compiling with Java command line compiler")
        outputContains("Compiling with toolchain '${someJdk.javaHome.absolutePath}'.")
        javaClassFile("Foo.class").exists()
    }

    @ToBeFixedForConfigurationCache(because = "Creates a second exception")
    def 'fails when requesting not available toolchain'() {
        buildFile << """
            apply plugin: 'java'

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                }
            }
"""

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
            .withArgument("-Porg.gradle.java.installations.auto-download=false")
            .withTasks("compileJava")
            .runWithFailure()

        then:
        failureHasCause('No compatible toolchains found for request filter: {languageVersion=99} (auto-detect true, auto-download false)')
    }

    @Requires(adhoc = { AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_7) != null })
    def "can use toolchains to compile java 1.7 code"() {
        def java7jdk = AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_7)
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(7)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        runWithToolchainConfigured(java7jdk)

        then:
        outputContains("Compiling with Java command line compiler")
        outputContains("Compiling with toolchain '${java7jdk.javaHome.absolutePath}'.")
        javaClassFile("Foo.class").exists()
    }

    @Requires(adhoc = { AvailableJavaHomes.getJdk(JavaVersion.VERSION_11) != null })
    def "uses matching compatibility options for source and target level"() {
        def jdk11 = AvailableJavaHomes.getJdk(JavaVersion.VERSION_11)
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(11)
                }
            }
        """

        file("src/main/java/Foo.java") << """
public class Foo {
    public void foo() {
        java.util.function.Function<String, String> append = (var string) -> string + " ";
    }
}
"""

        when:
        runWithToolchainConfigured(jdk11)

        then:
        outputContains("Compiling with toolchain '${jdk11.javaHome.absolutePath}'.")
        javaClassFile("Foo.class").exists()
    }

    @Requires(adhoc = { AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_8) != null })
    def "can use compile daemon with tools jar"() {
        def jdk8 = AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_8)
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(8)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        runWithToolchainConfigured(jdk8)

        then:
        outputDoesNotContain("Compiling with Java command line compiler")
        outputContains("Compiling with toolchain '${jdk8.javaHome.absolutePath}'.")
        javaClassFile("Foo.class").exists()
    }


    @ToBeFixedForConfigurationCache(because = "Storing the configuration causes the execution exception to be triggered")
    def 'cannot configure both toolchain and source and target compatibility at project level'() {
        def jdk = Jvm.current()
        buildFile << """
            apply plugin: 'java'

            java {
                sourceCompatibility = JavaVersion.VERSION_14
                targetCompatibility = JavaVersion.VERSION_14
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }
"""

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
            .withArgument("-Porg.gradle.java.installations.auto-detect=false")
            .withArgument("-Porg.gradle.java.installations.paths=" + jdk.javaHome.absolutePath)
            .withArgument("--info")
            .withTasks("compileJava")
            .runWithFailure()

        then:
        failureHasCause('The new Java toolchain feature cannot be used at the project level in combination with source and/or target compatibility')
    }

    def 'configuring toolchain and clearing source and target compatibility is supported'() {
        def jdk = Jvm.current()
        buildFile << """
            apply plugin: 'java'

            java {
                sourceCompatibility = JavaVersion.VERSION_14
                targetCompatibility = JavaVersion.VERSION_14
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
                sourceCompatibility = null
                targetCompatibility = null
            }

            compileJava {
                def projectSourceCompat = project.java.sourceCompatibility
                def projectTargetCompat = project.java.targetCompatibility
                doLast {
                    logger.lifecycle("project.sourceCompatibility = \$projectSourceCompat")
                    logger.lifecycle("project.targetCompatibility = \$projectTargetCompat")
                    logger.lifecycle("task.sourceCompatibility = \$sourceCompatibility")
                    logger.lifecycle("task.targetCompatibility = \$targetCompatibility")
                }
            }
"""

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        runWithToolchainConfigured(jdk)

        then:
        outputContains("project.sourceCompatibility = ${jdk.javaVersion}")
        outputContains("project.targetCompatibility = ${jdk.javaVersion}")
        outputContains("task.sourceCompatibility = ${jdk.javaVersion.majorVersion}")
        outputContains("task.targetCompatibility = ${jdk.javaVersion.majorVersion}")
    }

    @Unroll
    @Requires(adhoc = { AvailableJavaHomes.getJdk(JavaVersion.VERSION_11) != null })
    def 'source and target compatibility reflect toolchain usage (source #source, target #target)'() {
        def jdk11 = AvailableJavaHomes.getJdk(JavaVersion.VERSION_11)
        buildFile << """
            apply plugin: 'java'

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(11)
                }
            }

            compileJava {
                if ("$source" != 'none')
                    sourceCompatibility = JavaVersion.toVersion($source)
                if ("$target" != 'none')
                    targetCompatibility = JavaVersion.toVersion($target)
                def projectSourceCompat = project.java.sourceCompatibility
                def projectTargetCompat = project.java.targetCompatibility
                doLast {
                    logger.lifecycle("project.sourceCompatibility = \$projectSourceCompat")
                    logger.lifecycle("project.targetCompatibility = \$projectTargetCompat")
                    logger.lifecycle("task.sourceCompatibility = \$sourceCompatibility")
                    logger.lifecycle("task.targetCompatibility = \$targetCompatibility")
                }
            }
"""

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        runWithToolchainConfigured(jdk11)

        then:
        outputContains("project.sourceCompatibility = 11")
        outputContains("project.targetCompatibility = 11")
        outputContains("task.sourceCompatibility = $sourceOut")
        outputContains("task.targetCompatibility = $targetOut")

        where:
        source  | target    | sourceOut | targetOut
        '9'     | '10'      | '1.9'     | '1.10'
        '9'     | 'none'    | '1.9'     | '11'
        'none'  | 'none'    | '11'      | '11'

    }

    def runWithToolchainConfigured(Jvm jvm) {
        result = executer
            .withArgument("-Porg.gradle.java.installations.auto-detect=false")
            .withArgument("-Porg.gradle.java.installations.paths=" + jvm.javaHome.absolutePath)
            .withArgument("--info")
            .withTasks("compileJava")
            .run()
    }

}
