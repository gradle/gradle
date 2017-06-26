/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class ApplicationPluginUnixShellsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        createSampleProjectSetup()
    }

    def cleanup() {
        testDirectory.usingNativeTools().deleteDir() //remove symlinks
    }

    public static boolean shellAvailable(String shellCommand) {
        return TestPrecondition.UNIX_DERIVATIVE.isFulfilled() && (
            new File("/bin/$shellCommand").exists()
                || new File("/usr/bin/$shellCommand").exists()
                || new File("/opt/local/bin/$shellCommand").exists());
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("bash") })
    def "can execute generated Unix start script in Bash"() {
        given:
        succeeds('installDist')

        when:
        ExecutionResult result = runViaUnixStartScript("bash")

        then:
        result.output.contains('Hello World!')
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("dash") })
    def "can execute generated Unix start script in Dash"() {
        given:
        succeeds('installDist')

        when:
        ExecutionResult result = runViaUnixStartScript("dash")

        then:
        result.output.contains('Hello World!')
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("static-sh") })
    def "can execute generated Unix start script in BusyBox"() {
        given:
        succeeds('installDist')

        when:
        ExecutionResult result = runViaUnixStartScript("static-sh")

        then:
        result.output.contains('Hello World!')
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("bash") })
    def "can use APP_HOME in DEFAULT_JVM_OPTS with custom start script in Bash"() {
        given:
        extendBuildFileWithAppHomeProperty()
        succeeds('installDist')

        when:
        ExecutionResult result = runViaUnixStartScript("bash")

        then:
        result.assertOutputContains("App Home: ${file('build/install/sample').absolutePath}")
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("dash") })
    def "can use APP_HOME in DEFAULT_JVM_OPTS with custom start script in Dash"() {
        given:
        extendBuildFileWithAppHomeProperty()
        succeeds('installDist')

        when:
        ExecutionResult result = runViaUnixStartScript("dash")

        then:
        result.assertOutputContains("App Home: ${file('build/install/sample').absolutePath}")
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("static-sh") })
    def "can use APP_HOME in DEFAULT_JVM_OPTS with custom start script in BusyBox"() {
        given:
        extendBuildFileWithAppHomeProperty()
        succeeds('installDist')

        when:
        ExecutionResult result = runViaUnixStartScript("static-sh")

        then:
        result.assertOutputContains("App Home: ${file('build/install/sample').absolutePath}")
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("bash") })
    def "can pass argument to App with custom start script in Bash"() {
        given:
        succeeds('installDist')

        when:
        ExecutionResult result = runViaUnixStartScript("bash", "someArg1", "someArg1", "some arg 2", "-DFOO=\\\"bar < baz\\\"", "-DGOO='car < caz'")

        then:
        result.output.contains('Arg: someArg1')
        result.output.contains('Arg: some arg 2')
        result.output.contains('Arg: -DFOO="bar < baz"')
        result.output.contains('Arg: -DGOO=\'car < caz\'')
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("dash") })
    def "can pass argument to App with custom start script in Dash"() {
        given:
        succeeds('installDist')

        when:
        ExecutionResult result = runViaUnixStartScript("dash", "someArg1", "some arg 2", "-DFOO=\\\"bar < baz\\\"", "-DGOO='car < caz'")

        then:
        result.output.contains('Arg: someArg1')
        result.output.contains('Arg: some arg 2')
        result.output.contains('Arg: -DFOO="bar < baz"')
        result.output.contains('Arg: -DGOO=\'car < caz\'')
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("static-sh") })
    def "can pass argument to App with custom start script in BusyBox"() {
        given:
        succeeds('installDist')

        when:
        ExecutionResult result = runViaUnixStartScript("static-sh", "someArg1", "some arg 2", "-DFOO=\\\"bar < baz\\\"", "-DGOO='car < caz'")

        then:
        result.output.contains('Arg: someArg1')
        result.output.contains('Arg: some arg 2')
        result.output.contains('Arg: -DFOO="bar < baz"')
        result.output.contains('Arg: -DGOO=\'car < caz\'')
    }

    ExecutionResult runViaUnixStartScript(String shCommand, String... args) {
        TestFile startScriptDir = file('build/install/sample/bin')
        buildFile << """
task execStartScript(type: Exec) {
    workingDir '$startScriptDir.canonicalPath'
    commandLine './sample'
    args "${args.join('", "')}"
}
"""
        def path = setUpTestPATH(shCommand);
        return executer.withEnvironmentVars('PATH': path).withTasks('execStartScript').run();
    }

    private String setUpTestPATH(String shCommand) {
        def binDir = file('fake-bin')
        def basicCommands = ['basename', 'dirname', 'uname', 'which', 'sed', 'java']
        basicCommands.each { linkToBinary(it, it, binDir) }
        linkToBinary("sh", shCommand, binDir) // link the shell we want to use to 'sh' which the script will pick up using '#!/usr/bin/env sh'
        return binDir.absolutePath
    }

    private void linkToBinary(String command, String linkToCommand, TestFile binDir) {
        binDir.mkdirs()
        def binary = new File("/usr/bin/$linkToCommand")
        if (!binary.exists()) {
            binary = new File("/bin/$linkToCommand")
        }
        if (!binary.exists()) {
            binary = new File("/opt/local/bin/$linkToCommand")
        }
        assert binary.exists()

        binDir.file(command).createLink(binary)
    }

    private void createSampleProjectSetup() {
        createMainClass()
        populateBuildFile()
        populateSettingsFile()
    }

    private void extendBuildFileWithAppHomeProperty() {
        buildFile << """
applicationDefaultJvmArgs = ["-DappHomeSystemProp=REPLACE_THIS_WITH_APP_HOME"]

startScripts {
    doLast {
        unixScript.text = unixScript.text.replace("REPLACE_THIS_WITH_APP_HOME", "'\\\$APP_HOME'")
    }
}
"""
    }

    private void createMainClass() {
        file('src/main/java/org/gradle/test/Main.java') << """
package org.gradle.test;

public class Main {
    public static void main(String[] args) {
        System.out.println("App Home: " + System.getProperty("appHomeSystemProp"));
        System.out.println("Hello World!");
        for (String arg : args) {
            System.out.println("Arg: " + arg);
        }
    }
}
"""
    }

    private void populateBuildFile() {
        buildFile << """
apply plugin: 'application'

mainClassName = 'org.gradle.test.Main'
"""
    }

    private void populateSettingsFile() {
        settingsFile << """
rootProject.name = 'sample'
"""
    }
}
