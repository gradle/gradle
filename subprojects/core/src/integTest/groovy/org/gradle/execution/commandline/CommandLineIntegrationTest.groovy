/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.execution.commandline

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.internal.GFileUtils
import spock.lang.Issue

@SuppressWarnings('IntegrationTestFixtures')
class CommandLineIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        NativeServicesTestFixture.initialize()
    }

    def "has non zero exit code on build failure"() {
        ExecutionFailure failure = executer.withTasks('unknown').runWithFailure()
        failure.assertHasDescription("Task 'unknown' not found in root project 'commandLine'.")
    }

    def createProject() {
        buildFile """
            import org.gradle.internal.jvm.Jvm

            def providers = project.providers

            task checkJavaHome {
                doFirst {
                    assert Jvm.current().javaHome == new File(providers.gradleProperty('expectedJavaHome').get())
                }
            }

            task checkSystemProperty {
                def custom1 = project.providers.systemProperty('customProp1')
                def custom2 = project.providers.systemProperty('customProp2')
                doLast {
                    assert custom1.orNull == 'custom-value'
                    assert custom2.orNull == 'custom value'
                }
            }
        """

        settingsFile """
            rootProject.name = 'commandLine'
        """
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    def "can define java home using environment variable"() {
        setup:
        createProject()

        when:
        String javaHome = Jvm.current().javaHome
        String expectedJavaHome = "-PexpectedJavaHome=${javaHome}"

        then:
        // Handle JAVA_HOME specified
        executer.withJavaHome(javaHome).withArguments(expectedJavaHome).withTasks('checkJavaHome').run()

        // Handle JAVA_HOME with trailing separator
        executer.withJavaHome(javaHome + File.separator).withArguments(expectedJavaHome).withTasks('checkJavaHome').run()

        if (!OperatingSystem.current().isWindows()) {
            return
        }

        // Handle JAVA_HOME wrapped in quotes
        executer.withJavaHome("\"$javaHome\"").withArguments(expectedJavaHome).withTasks('checkJavaHome').run()

        // Handle JAVA_HOME with slash separators. This is allowed by the JVM
        executer.withJavaHome(javaHome.replace(File.separator, '/')).withArguments(expectedJavaHome).withTasks('checkJavaHome').run()
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    def "uses java command from path when java home not specified"() {
        setup:
        createProject()

        when:
        def jvm = Jvm.current()
        String expectedJavaHome = "-PexpectedJavaHome=${(jvm.javaHome.canonicalPath)}"
        String path = String.format('%s%s%s', jvm.javaExecutable.parentFile.canonicalPath, File.pathSeparator, System.getenv('PATH'))

        then:
        executer.withEnvironmentVars('PATH': path).withJavaHome('').withArguments(expectedJavaHome).withTasks('checkJavaHome').run()
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    def "fails when java home does not point to a java installation"() {
        when:
        def failure = executer.withJavaHome(testDirectory.absolutePath).withTasks('checkJavaHome').runWithFailure()

        then:
        failure.error.contains('ERROR: JAVA_HOME is set to an invalid directory')
    }

    @Requires([UnitTestPreconditions.Symlinks, IntegTestPreconditions.NotEmbeddedExecutor])
    def "fails when java home not set and path does not contain java"() {
        when:
        def links = ['basename', 'dirname', 'uname', 'which', 'sed', 'sh', 'bash']
        def binDir = file('fake-bin')

        then:
        def path
        if (OperatingSystem.current().windows) {
            path = ''
        } else {
            // Set up a fake bin directory, containing the things that the script needs, minus any java that might be in /usr/bin
            links.each { linkToBinary(it, binDir) }
            path = binDir.absolutePath
        }

        def failure = executer.withEnvironmentVars('PATH': path).withJavaHome('').withTasks('checkJavaHome').runWithFailure()
        failure.error.contains("ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.")

        cleanup:
        links.each {
            new File(getTestDirectory(), "fake-bin/$it").delete()
        }
    }

    def linkToBinary(String command, TestFile binDir) {
        binDir.mkdirs()
        def binary = new File("/usr/bin/$command")
        if (!binary.exists()) {
            binary = new File("/bin/$command")
        }
        assert binary.exists()
        binDir.file(command).createLink(binary)
    }

    def "can define gradle user home via environment variable"() {
        // the actual testing is done in the build script.
        when:
        buildFile """
            task checkGradleUserHomeViaSystemEnv {
                def gradleUserHomeDir = gradle.gradleUserHomeDir
                def customUserHome = file('customUserHome')
                doLast {
                    assert gradleUserHomeDir == customUserHome
                }
            }
        """

        File gradleUserHomeDir = file('customUserHome')

        then:
        executer
            .withOwnUserHomeServices()
            .withGradleUserHomeDir(null)
            .withEnvironmentVars('GRADLE_USER_HOME': gradleUserHomeDir.absolutePath)
            .withTasks("checkGradleUserHomeViaSystemEnv")
            .run()
    }

    @Requires([UnitTestPreconditions.NotEC2Agent, IntegTestPreconditions.NotEmbeddedExecutor])
    @Issue('https://github.com/gradle/gradle-private/issues/2876')
    def "check default gradle user home"() {
        given:
        buildFile """
            task checkDefaultGradleUserHome {
                def gradleUserHomeDir = gradle.gradleUserHomeDir
                doLast {
                    assert gradleUserHomeDir == new File(System.properties['user.home'], ".gradle")
                }
            }
        """

        when:
        // the actual testing is done in the build script.
        File userHome = file('customUserHome')
        executer
            .withOwnUserHomeServices()
            .withUserHomeDir(userHome)
            .withGradleUserHomeDir(null)
            .withTasks("checkDefaultGradleUserHome")
            .run()

        then:
        userHome.file(".gradle").exists()
    }

    void "can specify system properties from command line"() {
        when:
        createProject()
        // the actual testing is done in the build script.
        then:
        executer.withTasks("checkSystemProperty").withArguments('-DcustomProp1=custom-value', '-DcustomProp2=custom value').run();
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    def "can specify system properties using GRADLE_OPTS environment variable"() {
        when:
        createProject()

        then:
        // the actual testing is done in the build script.
        executer.withTasks("checkSystemProperty").withEnvironmentVars("GRADLE_OPTS": '-DcustomProp1=custom-value "-DcustomProp2=custom value"').run();
    }

    @Requires([UnitTestPreconditions.Unix, IntegTestPreconditions.NotEmbeddedExecutor])
    def "can specify system properties using gradle opts environment variable with line breaks"() {
        when:
        createProject()

        then:
        // the actual testing is done in the build script.
        executer.withTasks("checkSystemProperty").withEnvironmentVars("GRADLE_OPTS": """
            -DcustomProp1=custom-value
            "-DcustomProp2=custom value"
        """).run();
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    def "can specify system properties using JAVA_OPTS environment variable"() {
        when:
        createProject()

        then:
        // the actual testing is done in the build script.
        executer.withTasks("checkSystemProperty").withEnvironmentVars("JAVA_OPTS": '-DcustomProp1=custom-value "-DcustomProp2=custom value"').run();
    }

    def "allows reconfiguring project cache dir with relative directory"() {
        given:
        buildFile "task foo { outputs.file file('out'); doLast { } }"

        when:
        executer.withTasks("foo").withArguments("--project-cache-dir", ".foo").run()

        then:
        assert file(".foo").exists()
    }

    def "allows reconfiguring project cache directory with absolute directory"() {
        given:
        file("build.gradle").write "task foo { outputs.file file('out'); doLast { } }"
        File someAbsoluteDir = file("foo/bar/baz").absoluteFile
        assert someAbsoluteDir.absolute

        when:
        executer.withTasks("foo").withArguments("--project-cache-dir", someAbsoluteDir.toString()).run()

        then:
        someAbsoluteDir.exists()
    }

    def "system property GRADLE_USER_HOME has precedence over environment variable"() {
        given:
        buildFile """
            task checkSystemPropertyGradleUserHomeHasPrecedence {
                def gradleUserHomeDir = gradle.gradleUserHomeDir
                def systemPropCustomUserHome = file('systemPropCustomUserHome')
                doLast {
                    assert gradleUserHomeDir == systemPropCustomUserHome
                }
            }
        """

        when:
        // the actual testing is done in the build script.
        File gradleUserHomeDir = file("customUserHome")
        File systemPropGradleUserHomeDir = file("systemPropCustomUserHome")

        then:
        executer
            .withOwnUserHomeServices()
            .withGradleUserHomeDir(null)
            .withArguments("-Dgradle.user.home=" + systemPropGradleUserHomeDir.absolutePath)
            .withEnvironmentVars('GRADLE_USER_HOME': gradleUserHomeDir.absolutePath)
            .withTasks("checkSystemPropertyGradleUserHomeHasPrecedence")
            .run()
    }

    @Requires([UnitTestPreconditions.Symlinks, IntegTestPreconditions.NotEmbeddedExecutor])
    def "resolves links when determining home directory"() {
        when:
        def script = file('bin/my app')
        script.parentFile.createDir()
        script.createLink(distribution.gradleHomeDir.file('bin/gradle'))

        def result = executer.usingExecutable(script.absolutePath).withTasks("help").run()

        then:
        result.output.contains("my app")

        // Don't follow links when cleaning up test files
        testDirectory.usingNativeTools().deleteDir()
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    def "uses script basename as application name for use in log messages"() {
        when:
        def binDir = distribution.gradleHomeDir.file('bin')
        def newScript = binDir.file(OperatingSystem.current().getScriptName('my app'))

        binDir.file(OperatingSystem.current().getScriptName('gradle')).copyTo(newScript)
        newScript.permissions = 'rwx------'

        then:
        def result = executer.usingExecutable(newScript.absolutePath).withTasks("help").run()
        result.output.contains("my app")

        cleanup:
        GFileUtils.forceDelete(newScript)
    }
}
