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

package org.gradle.api.tasks

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Assume
import spock.lang.Issue

/**
 * Tests running JavaExec tasks, ExecOperations.javaexec and ProviderFactory.javaexec
 * with JVM versions different from the current Daemon JVM.
 */
class JavaExecJavaVersionIntegrationSpec extends AbstractIntegrationSpec implements JavaToolchainFixture {

    def "up-to-date when executing JavaExec task twice in a row with the same java version"() {
        given:
        configureJavaExecTask()

        when:
        runWith(jdk)
        succeeds "runHelloWorld"

        then:
        executedAndNotSkipped ":runHelloWorld"
        assertExecutedWith(jdk)

        when:
        runWith(jdk)
        succeeds "runHelloWorld"

        then:
        skipped ":runHelloWorld"

        where:
        jdk << AvailableJavaHomes.allJdkVersions
    }

    @Requires(IntegTestPreconditions.DifferentJdkAvailable)
    @Issue("https://github.com/gradle/gradle/issues/6694")
    def "not up-to-date when executing JavaExec task twice in a row with a different java versions"() {
        given:
        configureJavaExecTask()

        when:
        runWith(Jvm.current())
        succeeds "runHelloWorld"

        then:
        executedAndNotSkipped ":runHelloWorld"
        assertExecutedWith(Jvm.current())

        when:
        def otherJdk = AvailableJavaHomes.differentVersion
        runWith(otherJdk)
        succeeds "runHelloWorld", "--info"

        then:
        executedAndNotSkipped ":runHelloWorld"
        assertExecutedWith(otherJdk)
        output.contains "Value of input property 'javaLauncher.metadata.languageVersion' has changed for task ':runHelloWorld'"
    }

    @Issue("https://github.com/gradle/gradle/issues/6694")
    def "up-to-date when the Java executable changes but the version does not"() {
        // We must have at least two JDKs of the same version for this test
        Assume.assumeTrue(jdks.size() > 1)
        // it doesn't work if current JAVA_HOME has same major version, because current JVM is always used
        Assume.assumeTrue(jdks[0].javaVersionMajor != Jvm.current().javaVersionMajor)

        given:
        configureJavaExecTask()

        when:
        runWith(jdks[0])
        succeeds "runHelloWorld"

        then:
        executedAndNotSkipped ":runHelloWorld"
        assertExecutedWith(jdks[0])

        when:
        runWith(jdks[1])
        succeeds "runHelloWorld"

        then:
        skipped ":runHelloWorld"

        where:
        jdks << AvailableJavaHomes.getAvailableJdksByVersion().values()
    }

    def "can execute ExecOperations.javaexec on java #jvm"() {
        given:
        configureExecOperationTask()

        when:
        runWith(jvm)
        succeeds("runHelloWorld")

        then:
        assertExecutedWith(jvm)

        where:
        jvm << AvailableJavaHomes.allJdkVersions
    }

    def "can execute ProviderFactory.javaexec on java #jvm"() {
        given:
        configureProviderFactoryTask()

        when:
        runWith(jvm)
        succeeds("runHelloWorld")

        then:
        assertExecutedWith(jvm)

        where:
        jvm << AvailableJavaHomes.allJdkVersions
    }

    private static String getBaseProject() {
        """
            plugins {
                id("java-library")
            }

            def launcher = javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(providers.systemProperty("execJavaVersion").get())
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(providers.systemProperty("execJavaVersion").get())
                }
            }
        """
    }

    private void configureJavaExecTask() {
        buildFile << """
            ${baseProject}

            task runHelloWorld(type: JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = "Hello"
                outputs.dir layout.buildDirectory.dir("foo") // Required for up-to-date checks
                javaLauncher = launcher
            }
        """

        withHelloJava()
    }

    private void configureExecOperationTask() {
        buildFile << """
            ${baseProject}

            abstract class ExecOperationsTask extends DefaultTask {

                @Inject
                abstract ExecOperations getExecOperations()

                @Input
                abstract Property<JavaLauncher> getJavaLauncher()

                @InputFiles
                abstract ConfigurableFileCollection getClasspath()

                @TaskAction
                void exec() {
                    execOperations.javaexec {
                        classpath = this.getClasspath()
                        mainClass = "Hello"
                        executable = this.getJavaLauncher().get().getExecutablePath().asFile
                    }
                }

            }

            tasks.register("runHelloWorld", ExecOperationsTask) {
                classpath = sourceSets.main.runtimeClasspath
                javaLauncher = launcher
            }

        """

        withHelloJava()
    }

    private void configureProviderFactoryTask() {
        buildFile << """
            ${baseProject}

            abstract class PrintingTask extends DefaultTask {

                @Input
                abstract Property<String> getText()

                @TaskAction
                void exec() {
                    System.out.println(getText().get())
                }

            }

            tasks.register("runHelloWorld", PrintingTask) {
                dependsOn(sourceSets.main.runtimeClasspath) // Ideally this would not be necessary.

                text = providers.javaexec {
                    classpath = sourceSets.main.runtimeClasspath
                    mainClass = "Hello"
                    executable = launcher.get().getExecutablePath().asFile
                }.standardOutput.asText
            }

        """

        withHelloJava()
    }

    private void withHelloJava() {
        file("src/main/java/Hello.java") << """
            public class Hello {
                public static void main(String[] args) {
                    System.out.println("Version: " + System.getProperty("java.specification.version"));
                    System.out.println("Java home: " + System.getProperty("java.home"));
                }
            }
        """
    }

    void runWith(Jvm jvm) {
        withInstallations(jvm)
        executer.withArgument("-DexecJavaVersion=${jvm.javaVersionMajor}")
    }

    void assertExecutedWith(Jvm jvm) {
        outputContains("Version: " + JavaVersion.toVersion(jvm.javaVersionMajor))
        outputContains("Java home: " + jvm.javaHome.absolutePath)
    }

}
