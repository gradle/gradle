/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.internal.GFileUtils
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import spock.lang.Issue

@SuppressWarnings('IntegrationTestFixtures')
class CommandLineIntegrationTest extends AbstractIntegrationTest {

    @Rule
    public final TestResources resources = new TestResources(testDirectoryProvider)

    @Before
    void setup() {
        NativeServicesTestFixture.initialize()
    }

    @Test
    void hasNonZeroExitCodeOnBuildFailure() {
        ExecutionFailure failure = executer.withTasks('unknown').runWithFailure()
        failure.assertHasDescription("Task 'unknown' not found in root project 'commandLine'.")
    }

    @Test
    void canDefineJavaHomeUsingEnvironmentVariable() {
        String javaHome = Jvm.current().javaHome
        String expectedJavaHome = "-PexpectedJavaHome=${javaHome}"

        // Handle JAVA_HOME specified
        executer.withEnvironmentVars('JAVA_HOME': javaHome).withArguments(expectedJavaHome).withTasks('checkJavaHome').run()

        // Handle JAVA_HOME with trailing separator
        executer.withEnvironmentVars('JAVA_HOME': javaHome + File.separator).withArguments(expectedJavaHome).withTasks('checkJavaHome').run()

        if (!OperatingSystem.current().isWindows()) {
            return
        }

        // Handle JAVA_HOME wrapped in quotes
        executer.withEnvironmentVars('JAVA_HOME': "\"$javaHome\"").withArguments(expectedJavaHome).withTasks('checkJavaHome').run()

        // Handle JAVA_HOME with slash separators. This is allowed by the JVM
        executer.withEnvironmentVars('JAVA_HOME': javaHome.replace(File.separator, '/')).withArguments(expectedJavaHome).withTasks('checkJavaHome').run()
    }

    @Test
    void usesJavaCommandFromPathWhenJavaHomeNotSpecified() {
        String javaHome = Jvm.current().javaHome
        String expectedJavaHome = "-PexpectedJavaHome=${javaHome}"

        String path = String.format('%s%s%s', Jvm.current().javaExecutable.parentFile, File.pathSeparator, System.getenv('PATH'))
        executer.withEnvironmentVars('PATH': path, 'JAVA_HOME': '').withArguments(expectedJavaHome).withTasks('checkJavaHome').run()
    }

    @Test
    void failsWhenJavaHomeDoesNotPointToAJavaInstallation() {
        Assume.assumeFalse(GradleContextualExecuter.embedded)

        def failure = executer.withEnvironmentVars('JAVA_HOME': testDirectory).withTasks('checkJavaHome').runWithFailure()
        if (OperatingSystem.current().isWindows()) {
            assert failure.output.contains('ERROR: JAVA_HOME is set to an invalid directory')
        } else {
            assert failure.error.contains('ERROR: JAVA_HOME is set to an invalid directory')
        }
    }

    @Test
    @Requires(UnitTestPreconditions.Symlinks)
    void failsWhenJavaHomeNotSetAndPathDoesNotContainJava() {
        Assume.assumeFalse(GradleContextualExecuter.embedded)

        def links = ['basename', 'dirname', 'uname', 'which', 'sed', 'sh', 'bash']
        def binDir = file('fake-bin')
        try {
            def path
            if (OperatingSystem.current().windows) {
                path = ''
            } else {
                // Set up a fake bin directory, containing the things that the script needs, minus any java that might be in /usr/bin
                links.each { linkToBinary(it, binDir) }
                path = binDir.absolutePath
            }

            def failure = executer.withEnvironmentVars('PATH': path, 'JAVA_HOME': '').withTasks('checkJavaHome').runWithFailure()
            assert failure.error.contains("ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.")
        } finally {
            links.each {
                new File(getTestDirectory(), "fake-bin/$it").delete()
            }
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

    @Test
    void canDefineGradleUserHomeViaEnvironmentVariable() {
        // the actual testing is done in the build script.
        File gradleUserHomeDir = file('customUserHome')
        executer
            .withOwnUserHomeServices()
            .withGradleUserHomeDir(null)
            .withEnvironmentVars('GRADLE_USER_HOME': gradleUserHomeDir.absolutePath)
            .withTasks("checkGradleUserHomeViaSystemEnv")
            .run()
    }

    @Test
    @Requires(UnitTestPreconditions.NotEC2Agent)
    @Issue('https://github.com/gradle/gradle-private/issues/2876')
    void checkDefaultGradleUserHome() {
        Assume.assumeFalse(GradleContextualExecuter.embedded)
        // the actual testing is done in the build script.
        File userHome = file('customUserHome')
        executer
            .withOwnUserHomeServices()
            .withUserHomeDir(userHome)
            .withGradleUserHomeDir(null)
            .withTasks("checkDefaultGradleUserHome")
            .run()
        assert userHome.file(".gradle").exists()
    }

    @Test
    void canSpecifySystemPropertiesFromCommandLine() {
        // the actual testing is done in the build script.
        executer.withTasks("checkSystemProperty").withArguments('-DcustomProp1=custom-value', '-DcustomProp2=custom value').run();
    }

    @Test
    void canSpecifySystemPropertiesUsingGradleOptsEnvironmentVariable() {
        Assume.assumeFalse(GradleContextualExecuter.embedded)
        // the actual testing is done in the build script.
        executer.withTasks("checkSystemProperty").withEnvironmentVars("GRADLE_OPTS": '-DcustomProp1=custom-value "-DcustomProp2=custom value"').run();
    }

    @Test
    @Requires(UnitTestPreconditions.UnixDerivative)
    void canSpecifySystemPropertiesUsingGradleOptsEnvironmentVariableWithLinebreaks() {
        Assume.assumeFalse(GradleContextualExecuter.embedded)
        // the actual testing is done in the build script.
        executer.withTasks("checkSystemProperty").withEnvironmentVars("GRADLE_OPTS": """
            -DcustomProp1=custom-value
            "-DcustomProp2=custom value"
        """).run();
    }

    @Test
    void canSpecifySystemPropertiesUsingJavaOptsEnvironmentVariable() {
        Assume.assumeFalse(GradleContextualExecuter.embedded)
        // the actual testing is done in the build script.
        executer.withTasks("checkSystemProperty").withEnvironmentVars("JAVA_OPTS": '-DcustomProp1=custom-value "-DcustomProp2=custom value"').run();
    }

    @Test
    void allowsReconfiguringProjectCacheDirWithRelativeDir() {
        //given
        file("build.gradle").write "task foo { outputs.file file('out'); doLast { } }"

        //when
        executer.withTasks("foo").withArguments("--project-cache-dir", ".foo").run()

        //then
        assert file(".foo").exists()
    }

    @Test
    void allowsReconfiguringProjectCacheDirWithAbsoluteDir() {
        //given
        file("build.gradle").write "task foo { outputs.file file('out'); doLast { } }"
        File someAbsoluteDir = file("foo/bar/baz").absoluteFile
        assert someAbsoluteDir.absolute

        //when
        executer.withTasks("foo").withArguments("--project-cache-dir", someAbsoluteDir.toString()).run()

        //then
        assert someAbsoluteDir.exists()
    }

    @Test
    void systemPropGradleUserHomeHasPrecedenceOverEnvVariable() {
        // the actual testing is done in the build script.
        File gradleUserHomeDir = file("customUserHome")
        File systemPropGradleUserHomeDir = file("systemPropCustomUserHome")
        executer
            .withOwnUserHomeServices()
            .withGradleUserHomeDir(null)
            .withArguments("-Dgradle.user.home=" + systemPropGradleUserHomeDir.absolutePath)
            .withEnvironmentVars('GRADLE_USER_HOME': gradleUserHomeDir.absolutePath)
            .withTasks("checkSystemPropertyGradleUserHomeHasPrecedence")
            .run()
    }

    @Test
    @Requires(UnitTestPreconditions.Symlinks)
    void resolvesLinksWhenDeterminingHomeDirectory() {
        Assume.assumeFalse(GradleContextualExecuter.embedded)

        def script = file('bin/my app')
        script.parentFile.createDir()
        script.createLink(distribution.gradleHomeDir.file('bin/gradle'))

        def result = executer.usingExecutable(script.absolutePath).withTasks("help").run()
        assert result.output.contains("my app")

        // Don't follow links when cleaning up test files
        testDirectory.usingNativeTools().deleteDir()
    }

    @Test
    void usesScriptBaseNameAsApplicationNameForUseInLogMessages() {
        Assume.assumeFalse(GradleContextualExecuter.embedded)

        def binDir = distribution.gradleHomeDir.file('bin')
        def newScript = binDir.file(OperatingSystem.current().getScriptName('my app'))
        try {
            binDir.file(OperatingSystem.current().getScriptName('gradle')).copyTo(newScript)
            newScript.permissions = 'rwx------'

            def result = executer.usingExecutable(newScript.absolutePath).withTasks("help").run()
            assert result.output.contains("my app")
        } finally {
            GFileUtils.forceDelete(newScript)
        }
    }
}
