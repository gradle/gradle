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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.DocumentationUtils
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.TextUtil
import spock.lang.Issue

import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.INFO_DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.integtests.fixtures.SuggestionsMessages.STACKTRACE_MESSAGE
import static org.junit.Assume.assumeNotNull
import static org.junit.Assume.assumeTrue

class JavaCompileToolchainIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {

    def setup() {
        file("src/main/java/Foo.java") << "public class Foo {}"
    }

    def "fails on toolchain and forkOptions mismatch when #when"() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.differentVersion

        buildFile << """
            apply plugin: "java"
        """

        if (tool != null) {
            configureTool(tool == "current" ? currentJdk : otherJdk)
        }
        if (javaHome != null) {
            configureForkOptionsJavaHome(javaHome == "current" ? currentJdk : otherJdk)
        }
        if (executable != null) {
            configureForkOptionsExecutable(executable == "current" ? currentJdk : otherJdk)
        }

        when:
        withInstallations(currentJdk, otherJdk).runAndFail(":compileJava")

        then:
        failureDescriptionStartsWith("Execution failed for task ':compileJava'.")
        failureHasCause("Toolchain from `${errorFor}` property on `ForkOptions` does not match toolchain from `javaCompiler` property")

        where:
        when                                  | tool    | javaHome  | executable | errorFor
        "java home disagrees with executable" | null    | "other"   | "current"  | "executable"
        "tool disagrees with executable"      | "other" | null      | "current"  | "executable"
        "tool disagrees with java home"       | "other" | "current" | null       | "javaHome"
        "tool disagrees with "                | "other" | "current" | "current"  | "javaHome"
    }

    def "fails on toolchain and forkOptions mismatch when #when (without java base plugin)"() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.differentVersion

        def compileWithVersion = [currentJdk, otherJdk].collect { it.javaVersion }.min()

        buildFile << """
            plugins {
                id 'jvm-toolchains'
            }

            task compileJava(type: JavaCompile) {
                classpath = project.layout.files()
                source = project.layout.files("src/main/java")
                destinationDirectory = project.layout.buildDirectory.dir("classes/java/main")
                sourceCompatibility = "${compileWithVersion}"
                targetCompatibility = "${compileWithVersion}"
            }
        """

        if (tool != null) {
            configureTool(tool == "current" ? currentJdk : otherJdk)
        }
        if (javaHome != null) {
            configureForkOptionsJavaHome(javaHome == "current" ? currentJdk : otherJdk)
        }
        if (executable != null) {
            configureForkOptionsExecutable(executable == "current" ? currentJdk : otherJdk)
        }

        when:
        withInstallations(currentJdk, otherJdk).runAndFail(":compileJava")

        then:
        failureDescriptionStartsWith("Execution failed for task ':compileJava'.")
        failureHasCause("Toolchain from `${errorForProperty}` property on `ForkOptions` does not match toolchain from `javaCompiler` property")

        where:
        when                                  | tool    | javaHome  | executable | errorForProperty
        "java home disagrees with executable" | null    | "other"   | "current"  | "executable"
        "tool disagrees with executable"      | "other" | null      | "current"  | "executable"
        "tool disagrees with java home"       | "other" | "current" | null       | "javaHome"
        "tool disagrees with "                | "other" | "current" | "current"  | "javaHome"
    }

    def "uses #what toolchain #when (with java plugin)"() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.differentVersion
        def selectJdk = { it == "other" ? otherJdk : it == "current" ? currentJdk : null }

        buildFile << """
            apply plugin: "java"
        """

        if (withTool != null) {
            configureTool(selectJdk(withTool))
        }
        if (withJavaHome != null) {
            configureForkOptionsJavaHome(selectJdk(withJavaHome))
        }
        if (withExecutable != null) {
            configureForkOptionsExecutable(selectJdk(withExecutable))
        }
        if (withJavaExtension != null) {
            configureJavaPluginToolchainVersion(selectJdk(withJavaExtension))
        }

        def targetJdk = selectJdk(target)

        when:
        withInstallations(currentJdk, otherJdk).run(":compileJava", "--info")

        then:
        executedAndNotSkipped(":compileJava")
        outputContains("Compiling with toolchain '${targetJdk.javaHome.absolutePath}'")
        classJavaVersion(javaClassFile("Foo.class")) == targetJdk.javaVersion

        where:
        // Some cases are skipped, because forkOptions (when configured) must match the resulting toolchain, otherwise the build fails
        what             | when                         | withTool | withJavaHome | withExecutable | withJavaExtension | target
        "current JVM"    | "when nothing is configured" | null     | null         | null           | null              | "current"
        "java extension" | "when configured"            | null     | null         | null           | "other"           | "other"
        "executable"     | "when configured"            | null     | null         | "other"        | null              | "other"
        "java home"      | "when configured"            | null     | "other"      | null           | null              | "other"
        "assigned tool"  | "when configured"            | "other"  | null         | null           | null              | "other"
        "executable"     | "over java extension"        | null     | null         | "other"        | "current"         | "other"
        "java home"      | "over java extension"        | null     | "other"      | null           | "current"         | "other"
        "assigned tool"  | "over java extension"        | "other"  | null         | null           | "current"         | "other"
    }

    def "uses #what toolchain #when (without java base plugin)"() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.differentVersion
        def selectJdk = { it == "other" ? otherJdk : it == "current" ? currentJdk : null }

        def compileWithVersion = [currentJdk, otherJdk].collect { it.javaVersion }.min()

        buildFile << """
            plugins {
                id 'jvm-toolchains'
            }

            task compileJava(type: JavaCompile) {
                classpath = project.layout.files()
                source = project.layout.files("src/main/java")
                destinationDirectory = project.layout.buildDirectory.dir("classes/java/main")
                sourceCompatibility = "${compileWithVersion}"
                targetCompatibility = "${compileWithVersion}"
            }
        """

        if (withTool != null) {
            configureTool(selectJdk(withTool))
        }
        if (withJavaHome != null) {
            configureForkOptionsJavaHome(selectJdk(withJavaHome))
        }
        if (withExecutable != null) {
            configureForkOptionsExecutable(selectJdk(withExecutable))
        }

        def targetJdk = selectJdk(target)

        when:
        withInstallations(currentJdk, otherJdk).run(":compileJava", "--info")

        then:
        executedAndNotSkipped(":compileJava")
        outputContains("Compiling with toolchain '${targetJdk.javaHome.absolutePath}'")
        classJavaVersion(javaClassFile("Foo.class")) == JavaVersion.toVersion(compileWithVersion)

        where:
        // Some cases are skipped, because forkOptions (when configured) must match the resulting toolchain, otherwise the build fails
        what            | when                                 | withTool | withJavaHome | withExecutable | target
        "current JVM"   | "when toolchains are not configured" | null     | null         | null           | "current"
        "executable"    | "when configured"                    | null     | null         | "other"        | "other"
        "java home"     | "when configured"                    | null     | "other"      | null           | "other"
        "assigned tool" | "when configured"                    | "other"  | null         | null           | "other"
    }

    def "uses toolchain from forkOptions #forkOption when it points outside of installations"() {
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.differentVersion

        def path = TextUtil.normaliseFileSeparators(otherJdk.javaHome.absolutePath.toString() + appendPath)

        def compatibilityVersion = [currentJdk, otherJdk].collect { it.javaVersion }.min()

        buildFile << """
            apply plugin: "java"

            compileJava {
                options.fork = true
                ${configure.replace("<path>", path)}
                sourceCompatibility = "${compatibilityVersion}"
                targetCompatibility = "${compatibilityVersion}"
            }
        """

        when:
        if (forkOption == "java home") {
            executer.expectDocumentedDeprecationWarning("The ForkOptions.setJavaHome(File) method has been deprecated. This is scheduled to be removed in Gradle 9.0. The 'javaHome' property of ForkOptions is deprecated and will be removed in Gradle 9. Use JVM toolchains or the 'executable' property instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_fork_options_java_home")
        }
        // not adding the other JDK to the installations
        withInstallations(currentJdk).run(":compileJava", "--info")

        then:
        executedAndNotSkipped(":compileJava")
        outputContains("Compiling with toolchain '${otherJdk.javaHome.absolutePath}'")
        classJavaVersion(javaClassFile("Foo.class")) == JavaVersion.toVersion(compatibilityVersion)

        where:
        forkOption   | configure                                       | appendPath
        "java home"  | 'options.forkOptions.javaHome = file("<path>")' | ''
        "executable" | 'options.forkOptions.executable = "<path>"'     | OperatingSystem.current().getExecutableName('/bin/javac')
    }

    @Issue("https://github.com/gradle/gradle/issues/22398")
    def "ignore #forkOption if not forking"() {
        def curJvm = Jvm.current()
        def otherJvm = AvailableJavaHomes.getDifferentJdk()
        def path = TextUtil.normaliseFileSeparators(otherJvm.javaHome.absolutePath + appendPath)

        buildFile << """
            apply plugin: "java"

            compileJava {
                // we do not set `options.fork = true`
                ${configure.replace("<path>", path)}
            }
        """

        when:
        if (forkOption == "java home") {
            executer.expectDocumentedDeprecationWarning("The ForkOptions.setJavaHome(File) method has been deprecated. This is scheduled to be removed in Gradle 9.0. The 'javaHome' property of ForkOptions is deprecated and will be removed in Gradle 9. Use JVM toolchains or the 'executable' property instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_fork_options_java_home")
        }
        run(":compileJava", "--info")

        then:
        executedAndNotSkipped(":compileJava")
        outputContains("Compiling with toolchain '${curJvm.javaHome.absolutePath}'")
        outputContains("Compiling with JDK Java compiler API")
        outputDoesNotContain("Compiling with Java command line compiler")
        outputDoesNotContain("Started Gradle worker daemon")
        classJavaVersion(javaClassFile("Foo.class")) == JavaVersion.toVersion(curJvm.javaVersion)

        where:
        forkOption   | configure                                       | appendPath
        "java home"  | 'options.forkOptions.javaHome = file("<path>")' | ''
        "executable" | 'options.forkOptions.executable = "<path>"'     | OperatingSystem.current().getExecutableName('/bin/javac')
    }

    def 'fails when requesting not available toolchain'() {
        buildFile << """
            apply plugin: 'java'

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                }
            }
        """

        when:
        failure = executer
            .withToolchainDetectionEnabled()
            .withTasks("compileJava")
            .runWithFailure()

        then:
        failure.assertHasCause("No locally installed toolchains match and toolchain auto-provisioning is not enabled.")
            .assertHasResolutions(
                DocumentationUtils.normalizeDocumentationLink("Learn more about toolchain auto-detection at https://docs.gradle.org/current/userguide/toolchains.html#sec:auto_detection."),
                STACKTRACE_MESSAGE,
                INFO_DEBUG,
                SCAN,
                GET_HELP)
    }

    @Requires(IntegTestPreconditions.Java7HomeAvailable)
    def "can use toolchains to compile java 1.7 code"() {
        def jdk = AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_7)
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(7)
                }
            }
        """

        when:
        withInstallations(jdk).run(":compileJava", "--info")

        then:
        outputContains("Compiling with Java command line compiler")
        outputContains("Compiling with toolchain '${jdk.javaHome.absolutePath}'.")
        classJavaVersion(javaClassFile("Foo.class")) == JavaVersion.toVersion(jdk.javaVersion)
    }

    def "uses correct vendor when selecting a toolchain"() {
        def jdk = Jvm.current()

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                    vendor = JvmVendorSpec.matching("${System.getProperty("java.vendor").toLowerCase()}")
                }
            }
        """

        when:
        withInstallations(jdk).run(":compileJava", "--info")

        then:
        outputContains("Compiling with toolchain '${jdk.javaHome.absolutePath}'.")
        classJavaVersion(javaClassFile("Foo.class")) == JavaVersion.toVersion(jdk.javaVersion)
    }

    def "fails if no toolchain has a matching vendor"() {
        def version = Jvm.current().javaVersion.majorVersion
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${version})
                    vendor = JvmVendorSpec.AMAZON
                }
            }
        """

        when:
        fails("compileJava")

        then:
        failure.assertHasCause("No locally installed toolchains match and toolchain auto-provisioning is not enabled.")
            .assertHasResolutions(
                DocumentationUtils.normalizeDocumentationLink("Learn more about toolchain auto-detection at https://docs.gradle.org/current/userguide/toolchains.html#sec:auto_detection."),
                STACKTRACE_MESSAGE,
                INFO_DEBUG,
                SCAN,
                GET_HELP)
    }

    def "fails if no toolchain has a compiler"() {
        def jre = AvailableJavaHomes.differentVersionJreOnly
        assumeNotNull(jre)
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jre.javaVersionMajor})
                }
            }
        """

        when:
        withInstallations(jre).fails("compileJava")

        then:
        failure.assertHasCause("No locally installed toolchains match and toolchain auto-provisioning is not enabled.")
            .assertHasResolutions(
                DocumentationUtils.normalizeDocumentationLink("Learn more about toolchain auto-detection at https://docs.gradle.org/current/userguide/toolchains.html#sec:auto_detection."),
                STACKTRACE_MESSAGE,
                INFO_DEBUG,
                SCAN,
                GET_HELP)
    }

    def "can use compile daemon with tools jar"() {
        def jdk = AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_8)
        assumeTrue(JavaVersion.current() != JavaVersion.VERSION_1_8)

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(8)
                }
            }
        """

        when:
        withInstallations(jdk).run(":compileJava", "--info")

        then:
        outputDoesNotContain("Compiling with Java command line compiler")
        outputContains("Compiling with toolchain '${jdk.javaHome.absolutePath}'.")
        classJavaVersion(javaClassFile("Foo.class")) == JavaVersion.toVersion(jdk.javaVersion)
    }

    def "can compile Java using different JDKs"() {
        def jdk = AvailableJavaHomes.getJdk(javaVersion)
        assumeNotNull(jdk)

        buildFile << """
            plugins {
                id("java")
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }
        """

        when:
        withInstallations(jdk).run(":compileJava", "--info")

        then:
        outputDoesNotContain("Compiling with Java command line compiler")
        outputContains("Compiling with toolchain '${jdk.javaHome.absolutePath}'.")
        classJavaVersion(javaClassFile("Foo.class")) == JavaVersion.toVersion(jdk.javaVersion)

        where:
        javaVersion << JavaVersion.values().findAll { it.isJava8Compatible() && it != JavaVersion.current() }
    }

    /**
     * This test covers the case where in Java8 the class name becomes fully qualified in the deprecation message which is
     * somehow caused by invoking javacTask.getElements() in the IncrementalCompileTask of the incremental compiler plugin.
     */
    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "Java deprecation messages with different JDKs"() {
        def jdk = javaVersion == JavaVersion.current() ? Jvm.current() : AvailableJavaHomes.getJdk(javaVersion)

        buildFile << """
            plugins {
                id("java")
            }
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }
            tasks.withType(JavaCompile).configureEach {
                options.compilerArgs << "-Xlint:deprecation"
            }
        """

        file("src/main/java/com/example/Foo.java") << """
            package com.example;
            public class Foo {
                @Deprecated
                public void foo() {}
            }
        """

        def fileWithDeprecation = file("src/main/java/com/example/Bar.java") << """
            package com.example;
            public class Bar {
                public void bar() {
                    new Foo().foo();
                }
            }
        """

        executer.expectDeprecationWarning("$fileWithDeprecation:5: warning: $deprecationMessage")

        when:
        withInstallations(jdk).run(":compileJava", "--info")

        then:
        outputDoesNotContain("Compiling with Java command line compiler")
        outputContains("Compiling with toolchain '${jdk.javaHome.absolutePath}'.")
        outputContains("Compiling with JDK Java compiler API.")
        javaClassFile("com/example/Foo.class").exists()
        javaClassFile("com/example/Bar.class").exists()

        where:
        javaVersion             | deprecationMessage
        JavaVersion.VERSION_1_8 | "[deprecation] foo() in com.example.Foo has been deprecated"
        JavaVersion.current()   | "[deprecation] foo() in Foo has been deprecated"
    }

    @Issue("https://github.com/gradle/gradle/issues/23990")
    def "can compile with a custom compiler executable"() {
        def otherJdk = AvailableJavaHomes.getJdk(JavaVersion.current())
        def jdk = AvailableJavaHomes.getDifferentVersion {
            def v = it.languageVersion.majorVersion.toInteger()
            11 <= v && v <= 18 // Java versions supported by ECJ releases used in the test
        }

        buildFile << """
            plugins {
                id("java")
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${otherJdk.javaVersion.majorVersion})
                }
            }

            configurations {
                ecj {
                    canBeConsumed = false
                    assert canBeResolved
                }
            }

            ${mavenCentralRepository()}

            dependencies {
                def changed = providers.gradleProperty("changed").isPresent()
                ecj(!changed ? "org.eclipse.jdt:ecj:3.31.0" : "org.eclipse.jdt:ecj:3.32.0")
            }

            // Make sure the provider is up-to-date only if the ECJ classpath does not change
            class EcjClasspathProvider implements CommandLineArgumentProvider {
                @Classpath
                final FileCollection ecjClasspath

                EcjClasspathProvider(FileCollection ecjClasspath) {
                    this.ecjClasspath = ecjClasspath
                }

                @Override
                List<String> asArguments() {
                    return ["-cp", ecjClasspath.asPath, "org.eclipse.jdt.internal.compiler.batch.Main"]
                 }
            }

            compileJava {
                def customJavaLauncher = javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(${jdk.javaVersion.majorVersion}))
                }.get()

                // ECJ does not support generating JNI headers
                options.headerOutputDirectory.set(provider { null })
                options.fork = true
                options.forkOptions.executable = customJavaLauncher.executablePath.asFile.absolutePath
                options.forkOptions.jvmArgumentProviders.add(new EcjClasspathProvider(configurations.ecj))
            }
        """

        when:
        withInstallations(jdk, otherJdk).run(":compileJava", "--info")
        then:
        executedAndNotSkipped(":compileJava")
        outputContains("Compiling with toolchain '${jdk.javaHome.absolutePath}'")
        outputContains("Compiling with Java command line compiler '${jdk.javaExecutable.absolutePath}'")
        classJavaVersion(javaClassFile("Foo.class")) == jdk.javaVersion

        // Test up-to-date checks
        when:
        withInstallations(jdk, otherJdk).run(":compileJava")
        then:
        skipped(":compileJava")

        when:
        withInstallations(jdk, otherJdk).run(":compileJava", "-Pchanged")
        then:
        executedAndNotSkipped(":compileJava")

        when:
        withInstallations(jdk, otherJdk).run(":compileJava", "-Pchanged")
        then:
        skipped(":compileJava")
    }

    private TestFile configureForkOptionsExecutable(Jvm jdk) {
        buildFile << """
            compileJava {
                options.fork = true
                options.forkOptions.executable = "${TextUtil.normaliseFileSeparators(jdk.javacExecutable.absolutePath)}"
            }
        """
    }

    private TestFile configureForkOptionsJavaHome(Jvm jdk) {
        executer.expectDocumentedDeprecationWarning("The ForkOptions.setJavaHome(File) method has been deprecated. This is scheduled to be removed in Gradle 9.0. The 'javaHome' property of ForkOptions is deprecated and will be removed in Gradle 9. Use JVM toolchains or the 'executable' property instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_fork_options_java_home")
        buildFile << """
            compileJava {
                options.fork = true
                options.forkOptions.javaHome = file("${TextUtil.normaliseFileSeparators(jdk.javaHome.absolutePath)}")
            }
        """
    }

    private TestFile configureTool(Jvm jdk) {
        buildFile << """
            compileJava {
                javaCompiler = javaToolchains.compilerFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }
        """
    }
}
