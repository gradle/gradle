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
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.nativeplatform.filesystem.FileSystems
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.PreconditionVerifier
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Before
import org.junit.Rule
import org.junit.Test

public class CommandLineIntegrationTest extends AbstractIntegrationTest {

    @Rule public final TestResources resources = new TestResources(testDirectoryProvider)
    @Rule public final PreconditionVerifier verifier = new PreconditionVerifier()

    @Before
    void setup() {
        executer.requireGradleHome()
    }

    @Test
    public void hasNonZeroExitCodeOnBuildFailure() {
        ExecutionFailure failure = executer.withTasks('unknown').runWithFailure()
        failure.assertHasDescription("Task 'unknown' not found in root project 'commandLine'.")
    }

    @Test
    public void canDefineJavaHomeUsingEnvironmentVariable() {
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
    public void usesJavaCommandFromPathWhenJavaHomeNotSpecified() {
        String javaHome = Jvm.current().javaHome
        String expectedJavaHome = "-PexpectedJavaHome=${javaHome}"

        String path = String.format('%s%s%s', Jvm.current().javaExecutable.parentFile, File.pathSeparator, System.getenv('PATH'))
        executer.withEnvironmentVars('PATH': path, 'JAVA_HOME': '').withArguments(expectedJavaHome).withTasks('checkJavaHome').run()
    }

    @Test
    public void failsWhenJavaHomeDoesNotPointToAJavaInstallation() {
        def failure = executer.withEnvironmentVars('JAVA_HOME': testDirectory).withTasks('checkJavaHome').runWithFailure()
        assert failure.output.contains('ERROR: JAVA_HOME is set to an invalid directory')
    }

    @Test
    @Requires(TestPrecondition.SYMLINKS)
    public void failsWhenJavaHomeNotSetAndPathDoesNotContainJava() {
        def path
        if (OperatingSystem.current().windows) {
            path = ''
        } else {
            // Set up a fake bin directory, containing the things that the script needs, minus any java that might be in /usr/bin
            def binDir = file('fake-bin')
            ['basename', 'dirname', 'uname', 'which', 'bash'].each { linkToBinary(it, binDir) }
            path = binDir.absolutePath
        }

        def failure = executer.withEnvironmentVars('PATH': path, 'JAVA_HOME': '').withTasks('checkJavaHome').runWithFailure()
        assert failure.output.contains("ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.")
    }

    def linkToBinary(String command, TestFile binDir) {
        binDir.mkdirs()
        def binary = new File("/usr/bin/$command")
        if (!binary.exists()) {
            binary = new File("/bin/$command")
        }
        assert binary.exists()
        FileSystems.default.createSymbolicLink(binDir.file(command), binary.absoluteFile)
    }

    @Test
    public void canDefineGradleUserHomeViaEnvironmentVariable() {
        // the actual testing is done in the build script.
        File gradleUserHomeDir = file('customUserHome')
        executer.withGradleUserHomeDir(null).withEnvironmentVars('GRADLE_USER_HOME': gradleUserHomeDir.absolutePath).withTasks("checkGradleUserHomeViaSystemEnv").run();
    }

    @Test
    public void checkDefaultGradleUserHome() {
        // the actual testing is done in the build script.
        executer.withGradleUserHomeDir(null).withTasks("checkDefaultGradleUserHome").run();
    }

    @Test
    public void canSpecifySystemPropertiesFromCommandLine() {
        // the actual testing is done in the build script.
        executer.withTasks("checkSystemProperty").withArguments('-DcustomProp1=custom-value', '-DcustomProp2=custom value').run();
    }

    @Test
    public void canSpecifySystemPropertiesUsingGradleOptsEnvironmentVariable() {
        // the actual testing is done in the build script.
        executer.withTasks("checkSystemProperty").withEnvironmentVars("GRADLE_OPTS": '-DcustomProp1=custom-value -DcustomProp2=custom value').run();
    }

    @Test
    public void canSpecifySystemPropertiesUsingJavaOptsEnvironmentVariable() {
        // the actual testing is done in the build script.
        executer.withTasks("checkSystemProperty").withEnvironmentVars("JAVA_OPTS": '-DcustomProp1=custom-value "-DcustomProp2=custom value"').run();
    }

    @Test
    public void allowsReconfiguringProjectCacheDirWithRelativeDir() {
        //given
        file("build.gradle").write "task foo { outputs.file file('out'); doLast { } }"

        //when
        executer.withTasks("foo").withArguments("--project-cache-dir", ".foo").run()

        //then
        assert file(".foo").exists()
    }

    @Test
    public void allowsReconfiguringProjectCacheDirWithAbsoluteDir() {
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
    public void systemPropGradleUserHomeHasPrecedenceOverEnvVariable() {
        // the actual testing is done in the build script.
        File gradleUserHomeDir = file("customUserHome")
        File systemPropGradleUserHomeDir = file("systemPropCustomUserHome")
        executer.withGradleUserHomeDir(null).withArguments("-Dgradle.user.home=" + systemPropGradleUserHomeDir.absolutePath).withEnvironmentVars('GRADLE_USER_HOME': gradleUserHomeDir.absolutePath).withTasks("checkSystemPropertyGradleUserHomeHasPrecedence").run()
    }

    @Test
    @Requires(TestPrecondition.SYMLINKS)
    public void resolvesLinksWhenDeterminingHomeDirectory() {
        def script = file('bin/my app')
        script.parentFile.createDir()
        FileSystems.default.createSymbolicLink(script, distribution.gradleHomeDir.file('bin/gradle'))

        def result = executer.usingExecutable(script.absolutePath).withTasks("help").run()
        assert result.output.contains("my app")

        // Don't follow links when cleaning up test files
        testDirectory.usingNativeTools().deleteDir()
    }

    @Test
    public void usesScriptBaseNameAsApplicationNameForUseInLogMessages() {
        def binDir = distribution.gradleHomeDir.file('bin')
        def newScript = binDir.file(OperatingSystem.current().getScriptName('my app'))
        binDir.file(OperatingSystem.current().getScriptName('gradle')).copyTo(newScript)
        newScript.permissions = 'rwx------'

        def result = executer.usingExecutable(newScript.absolutePath).withTasks("help").run()
        assert result.output.contains("my app")
    }
}
