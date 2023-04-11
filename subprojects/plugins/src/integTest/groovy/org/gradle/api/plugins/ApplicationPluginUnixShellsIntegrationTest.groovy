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
        if (testDirectoryProvider.cleanup) {
            testDirectory.usingNativeTools().deleteDir() //remove symlinks
        }
    }

    static boolean shellAvailable(String shellCommand) {
        return TestPrecondition.UNIX_DERIVATIVE.isFulfilled() && locate(shellCommand)
    }

    static File locate(String shellCommand) {
        return [
                new File("/bin/$shellCommand"),
                new File("/usr/bin/$shellCommand"),
                new File("/usr/local/bin/$shellCommand"),
                new File("/opt/local/bin/$shellCommand")
        ].find { it.exists() }
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("bash") })
    def "can execute generated Unix start script in Bash"() {
        given:
        succeeds('installDist')

        when:
        runViaUnixStartScript("bash")

        then:
        outputContains('Hello World!')
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("dash") })
    def "can execute generated Unix start script in Dash"() {
        given:
        succeeds('installDist')

        when:
        runViaUnixStartScript("dash")

        then:
        outputContains('Hello World!')
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("static-sh") })
    def "can execute generated Unix start script in BusyBox"() {
        given:
        succeeds('installDist')

        when:
        runViaUnixStartScript("static-sh")

        then:
        outputContains('Hello World!')
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("bash") })
    def "can use APP_HOME in DEFAULT_JVM_OPTS with custom start script in Bash"() {
        given:
        extendBuildFileWithAppHomeProperty()
        succeeds('installDist')

        when:
        runViaUnixStartScript("bash")

        then:
        outputContains("App Home: ${file('build/install/sample').absolutePath}")
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("dash") })
    def "can use APP_HOME in DEFAULT_JVM_OPTS with custom start script in Dash"() {
        given:
        extendBuildFileWithAppHomeProperty()
        succeeds('installDist')

        when:
        runViaUnixStartScript("dash")

        then:
        outputContains("App Home: ${file('build/install/sample').absolutePath}")
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("static-sh") })
    def "can use APP_HOME in DEFAULT_JVM_OPTS with custom start script in BusyBox"() {
        given:
        extendBuildFileWithAppHomeProperty()
        succeeds('installDist')

        when:
        runViaUnixStartScript("static-sh")

        then:
        outputContains("App Home: ${file('build/install/sample').absolutePath}")
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("bash") })
    def "can pass argument to App with custom start script in Bash"() {
        given:
        succeeds('installDist')

        when:
        runViaUnixStartScript("bash", "someArg1", "someArg1", "some arg 2", "-DFOO=\\\"bar < baz\\\"", "-DGOO='car < caz'")

        then:
        outputContains('Arg: someArg1')
        outputContains('Arg: some arg 2')
        outputContains('Arg: -DFOO="bar < baz"')
        outputContains('Arg: -DGOO=\'car < caz\'')
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("dash") })
    def "can pass argument to App with custom start script in Dash"() {
        given:
        succeeds('installDist')

        when:
        runViaUnixStartScript("dash", "someArg1", "some arg 2", "-DFOO=\\\"bar < baz\\\"", "-DGOO='car < caz'")

        then:
        outputContains('Arg: someArg1')
        outputContains('Arg: some arg 2')
        outputContains('Arg: -DFOO="bar < baz"')
        outputContains('Arg: -DGOO=\'car < caz\'')
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("static-sh") })
    def "can pass argument to App with custom start script in BusyBox"() {
        given:
        succeeds('installDist')

        when:
        runViaUnixStartScript("static-sh", "someArg1", "some arg 2", "-DFOO=\\\"bar < baz\\\"", "-DGOO='car < caz'")

        then:
        outputContains('Arg: someArg1')
        outputContains('Arg: some arg 2')
        outputContains('Arg: -DFOO="bar < baz"')
        outputContains('Arg: -DGOO=\'car < caz\'')
    }

    @Requires(adhoc = { TestPrecondition.JDK9_OR_LATER.fulfilled && ApplicationPluginUnixShellsIntegrationTest.shellAvailable("bash") })
    def "can execute generated Unix start script for Java module in Bash"() {
        given:
        turnSampleProjectIntoModule()
        succeeds('installDist')

        when:
        runViaUnixStartScript("bash")

        then:
        outputContains('Hello World!')
    }

    @Requires(adhoc = { TestPrecondition.JDK9_OR_LATER.fulfilled && ApplicationPluginUnixShellsIntegrationTest.shellAvailable("dash") })
    def "can execute generated Unix start script for Java module in Dash"() {
        given:
        turnSampleProjectIntoModule()
        succeeds('installDist')

        when:
        runViaUnixStartScript("dash")

        then:
        outputContains('Hello World!')
    }

    @Requires(adhoc = { TestPrecondition.JDK9_OR_LATER.fulfilled && ApplicationPluginUnixShellsIntegrationTest.shellAvailable("static-sh") })
    def "can execute generated Unix start script for Java module in BusyBox"() {
        given:
        turnSampleProjectIntoModule()
        succeeds('installDist')

        when:
        runViaUnixStartScript("static-sh")

        then:
        outputContains('Hello World!')
    }

    @Requires(adhoc = { ApplicationPluginUnixShellsIntegrationTest.shellAvailable("shellcheck") })
    def "generate start script passes shellcheck"() {
        given:
        succeeds('installDist')

        when:
        runViaUnixStartScript("shellcheck")
        then:
        noExceptionThrown()
    }

    ExecutionResult runViaUnixStartScript(String shCommand, String... args) {
        TestFile startScriptDir = file('build/install/sample/bin')
        buildFile << """
task execStartScript(type: Exec) {
    workingDir '$startScriptDir.canonicalPath'
    commandLine '${locate(shCommand).absolutePath}'
    args "./sample"
}
"""
        if (args.length > 0) {
            buildFile << """
                execStartScript.args "${args.join('", "')}"
            """
        }
        return succeeds('execStartScript')
    }

    private void createSampleProjectSetup() {
        createMainClass()
        populateBuildFile()
        populateSettingsFile()
    }

    private void turnSampleProjectIntoModule() {
        createModuleInfo()
        buildFile << """
application {
    mainModule.set('main.test')
}
"""
    }

    private void extendBuildFileWithAppHomeProperty() {
        buildFile << """
application.applicationDefaultJvmArgs = ["-DappHomeSystemProp=REPLACE_THIS_WITH_APP_HOME"]

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

    private void createModuleInfo() {
        file('src/main/java/module-info.java') << "module main.test {}"
    }

    private void populateBuildFile() {
        buildFile << """
apply plugin: 'application'

application {
    mainClass.set('org.gradle.test.Main')
}
"""
    }

    private void populateSettingsFile() {
        settingsFile << """
rootProject.name = 'sample'
"""
    }
}
