/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.integtests.tooling

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Assume
import spock.lang.Unroll

class ToolingApiJdkCompatibilityTest extends AbstractIntegrationSpec {

    TestFile projectDir

    def setup() {
        projectDir = temporaryFolder.testDirectory
        temporaryFolder.testDirectory.file("build.gradle") << ""
        temporaryFolder.testDirectory.file("settings.gradle") << "rootProject.name = 'target-project'"
    }

    @Unroll
    @Requires(adhoc = { TestPrecondition.JDK11_OR_LATER.fulfilled && !GradleContextualExecuter.embedded })
    def "Java #compilerJdkVersion.majorVersion client can launch task on Java #clientJdkVersion.majorVersion with Gradle #gradleVersion on Java #gradleDaemonJdkVersion.majorVersion"(JavaVersion compilerJdkVersion, JavaVersion clientJdkVersion, JavaVersion gradleDaemonJdkVersion, String gradleVersion) {
        setup:
        def tapiClientCompilerJdk = AvailableJavaHomes.getJdk(compilerJdkVersion)
        def gradleDaemonJdk = AvailableJavaHomes.getJdk(gradleDaemonJdkVersion)
        Assume.assumeTrue(tapiClientCompilerJdk && gradleDaemonJdk)

        def classesDir = new File("build/tmp/tapiCompatClasses")
        classesDir.mkdirs()
        "compileJava${compilerJdkVersion.majorVersion}TapiClient"(classesDir)

        def classLoader = new URLClassLoader([classesDir.toURI().toURL()] as URL[], getClass().classLoader)
        def tapiClient = classLoader.loadClass("org.gradle.integtests.tooling.ToolingApiCompatibilityClient")

        when:
        def output = ("current" == gradleVersion)
            ? tapiClient.runHelp(gradleVersion, projectDir, gradleDaemonJdk.javaHome, distribution.gradleHomeDir.absoluteFile)
            : tapiClient.runHelp(gradleVersion, projectDir, gradleDaemonJdk.javaHome)

        then:
        output.contains("BUILD SUCCESSFUL")

        cleanup:
        classesDir.deleteDir()

        where:
        compilerJdkVersion      | clientJdkVersion      | gradleDaemonJdkVersion  | gradleVersion
        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_6 | "2.6"    // minimum supported version for Tooling API
        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_6 | "2.14.1" // last Gradle version that can run on Java 1.6
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_6 | "2.6"
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_6 | "2.14.1"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_6 | "2.6"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_6 | "2.14.1"
        JavaVersion.VERSION_11  | JavaVersion.current() | JavaVersion.VERSION_1_6 | "2.6"
        JavaVersion.VERSION_11  | JavaVersion.current() | JavaVersion.VERSION_1_6 | "2.14.1"

        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_7 | "2.6"    // minimum supported version for Tooling API
        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_7 | "4.6"    // last version with reported regression
        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_7 | "4.10.3" // last Gradle version that can run on Java 1.7
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_7 | "2.6"
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_7 | "4.6"
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_7 | "4.10.3"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_7 | "2.6"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_7 | "4.6"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_7 | "4.10.3"
        JavaVersion.VERSION_11  | JavaVersion.current() | JavaVersion.VERSION_1_7 | "2.6"
        JavaVersion.VERSION_11  | JavaVersion.current() | JavaVersion.VERSION_1_7 | "4.6"
        JavaVersion.VERSION_11  | JavaVersion.current() | JavaVersion.VERSION_1_7 | "4.10.3"

        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "2.6"    // minimum supported version for Tooling API
        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "4.6"    // last version with reported regression
        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "4.7"    // first version that had no reported regression
        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "current"
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "2.6"
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "4.6"
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "4.7"
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "current"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "2.6"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "4.6"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "4.7"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "current"
        JavaVersion.VERSION_11  | JavaVersion.current() | JavaVersion.VERSION_1_8 | "2.6"
        JavaVersion.VERSION_11  | JavaVersion.current() | JavaVersion.VERSION_1_8 | "4.6"
        JavaVersion.VERSION_11  | JavaVersion.current() | JavaVersion.VERSION_1_8 | "4.7"
        JavaVersion.VERSION_11  | JavaVersion.current() | JavaVersion.VERSION_1_8 | "current"
    }

    private void compileJava6TapiClient(File targetDir) {
        // TODO We need to use java 1.8 compiler here with -target 6 argument because GradleConnector is compiled with java 8
        compileTapiClient(AvailableJavaHomes.getJdk8(), targetDir, '-source 6 -target 6')
    }

    private void compileJava7TapiClient(File targetDir) {
        // TODO We need to use java 1.8 compiler here with -target 7 argument because GradleConnector is compiled with java 8
        compileTapiClient(AvailableJavaHomes.getJdk8(), targetDir, '-source 7 -target 7')
    }

    private void compileJava8TapiClient(File targetDir) {
        compileTapiClient(AvailableJavaHomes.getJdk8(), targetDir)
    }

    private void compileJava11TapiClient(File targetDir) {
        compileTapiClient(AvailableJavaHomes.getJdk(JavaVersion.VERSION_11), targetDir)
    }

    private void compileTapiClient(Jvm jvm, File targetDir, String compilerArgs = '') {
        def classpath = System.getProperty("java.class.path")
        def options = new File(targetDir, "options")
        options.text = """-cp
$classpath
-d
$targetDir.absolutePath
${compilerArgs.replace(' ', '\n')}"""
        def sourcePath = getClass().classLoader.getResource("org/gradle/integtests/tooling/ToolingApiCompatibilityClient.java").file
        def compilationCommand = "$jvm.javacExecutable.absolutePath @$options.absolutePath $sourcePath"

        def compilationProcess = compilationCommand.execute()
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        ByteArrayOutputStream err = new ByteArrayOutputStream()
        compilationProcess.waitForProcessOutput(out, err)
        println out
        println err
    }
}
