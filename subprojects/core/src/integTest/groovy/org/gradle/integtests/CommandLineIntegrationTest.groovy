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

import org.gradle.integtests.fixtures.ExecutionFailure
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.TestResources
import org.gradle.util.Jvm
import org.gradle.util.OperatingSystem
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DistributionIntegrationTestRunner.class)
public class CommandLineIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule public final TestResources resources = new TestResources()

    @Test
    public void hasNonZeroExitCodeOnBuildFailure() {
        ExecutionFailure failure = executer.withTasks('unknown').runWithFailure()
        failure.assertHasDescription("Task 'unknown' not found in root project 'commandLine'.")
    }

    @Test
    public void canonicalisesWorkingDirectory() {
        File javaprojectDir;
        if (OperatingSystem.current().isWindows()) {
            javaprojectDir = new File(dist.samplesDir, 'java/QUICKS~1')
        } else if (!OperatingSystem.current().isCaseSensitiveFileSystem()) {
            javaprojectDir = new File(dist.samplesDir, 'JAVA/QuickStart')
        } else {
            javaprojectDir = new File(dist.samplesDir, 'java/multiproject/../quickstart')
        }
        executer.inDirectory(javaprojectDir).withTasks('classes').run()
    }

    @Test
    public void canDefineJavaHomeViaEnvironmentVariable() {
        String expectedJavaHome = "-PexpectedJavaHome=${System.properties['java.home']}"
        String javaHome = System.properties['java.home']

        // Handle on the system PATH, with no JAVA_HOME specified
        String path = String.format('%s%s%s', Jvm.current().binDir, File.pathSeparator, System.getenv('PATH'))
        executer.withEnvironmentVars('PATH': path, 'JAVA_HOME': '')
                .withArguments(expectedJavaHome)
                .withTasks('checkJavaHome')
                .run()

        // Handle JAVA_HOME specified
        executer.withEnvironmentVars('JAVA_HOME': javaHome)
                .withArguments(expectedJavaHome)
                .withTasks('checkJavaHome')
                .run()

        // Handle JAVA_HOME with trailing separator
        executer.withEnvironmentVars('JAVA_HOME': javaHome + File.separator)
                .withArguments(expectedJavaHome)
                .withTasks('checkJavaHome')
                .run()

        if (!OperatingSystem.current().isWindows()) {
            return
        }

        // Handle JAVA_HOME wrapped in quotes
        executer.withEnvironmentVars('JAVA_HOME': "\"$javaHome\"")
                .withArguments(expectedJavaHome)
                .withTasks('checkJavaHome')
                .run()

        // Handle JAVA_HOME with slash separators. This is allowed by the JVM
        executer.withEnvironmentVars('JAVA_HOME': javaHome.replace(File.separator, '/'))
                .withArguments(expectedJavaHome)
                .withTasks('checkJavaHome')
                .run()
    }

    @Test
    public void failsWhenJavaHomeDoetNotPointToAJavaInstallation() {
        def failure = executer.withEnvironmentVars('JAVA_HOME': dist.testDir)
                .withTasks('checkJavaHome')
                .runWithFailure()
        assert failure.output.contains('ERROR: JAVA_HOME is set to an invalid directory')
    }

    @Test
    public void canDefineGradleUserHomeViaEnvironmentVariable() {
        // the actual testing is done in the build script.
        File gradleUserHomeDir = dist.testDir.file('customUserHome')
        executer.withUserHomeDir(null)
                .withEnvironmentVars('GRADLE_USER_HOME': gradleUserHomeDir.absolutePath)
                .withTasks("checkGradleUserHomeViaSystemEnv")
                .run();
    }

    @Test
    public void checkDefaultGradleUserHome() {
        // the actual testing is done in the build script.
        executer.withUserHomeDir(null).
                withTasks("checkDefaultGradleUserHome")
                .run();
    }

    @Test @Ignore
    public void systemPropGradleUserHomeHasPrecedenceOverEnvVariable() {
        // the actual testing is done in the build script.
        File gradleUserHomeDir = dist.testFile("customUserHome")
        File systemPropGradleUserHomeDir = dist.testFile("systemPropCustomUserHome")
        executer.withUserHomeDir(null)
                .withArguments("-Dgradle.user.home=" + systemPropGradleUserHomeDir.absolutePath)
                .withEnvironmentVars('GRADLE_USER_HOME': gradleUserHomeDir.absolutePath)
                .withTasks("checkSystemPropertyGradleUserHomeHasPrecedence")
                .run()
    }
}