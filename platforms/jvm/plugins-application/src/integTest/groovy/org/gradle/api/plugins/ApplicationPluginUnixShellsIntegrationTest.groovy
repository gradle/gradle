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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.PluginTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions

class ApplicationPluginUnixShellsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        createSampleProjectSetup()
    }

    def cleanup() {
        if (testDirectoryProvider.cleanup) {
            testDirectory.usingNativeTools().deleteDir() //remove symlinks
        }
    }

    @Requires([UnitTestPreconditions.UnixDerivative, PluginTestPreconditions.BashAvailable])
    def "can execute generated Unix start script in Bash"() {
        given:
        succeeds('installDist')

        when:
        runViaUnixStartScript("bash")

        then:
        outputContains('Hello World!')
    }

    @Requires([UnitTestPreconditions.UnixDerivative, PluginTestPreconditions.DashAvailable])
    def "can execute generated Unix start script in Dash"() {
        given:
        succeeds('installDist')

        when:
        runViaUnixStartScript("dash")

        then:
        outputContains('Hello World!')
    }

    @Requires([UnitTestPreconditions.UnixDerivative, PluginTestPreconditions.StaticShAvailable])
    def "can execute generated Unix start script in BusyBox"() {
        given:
        succeeds('installDist')

        when:
        runViaUnixStartScript("static-sh")

        then:
        outputContains('Hello World!')
    }

    @Requires([UnitTestPreconditions.UnixDerivative, PluginTestPreconditions.BashAvailable])
    def "can use APP_HOME in DEFAULT_JVM_OPTS with custom start script in Bash"() {
        given:
        extendBuildFileWithAppHomeProperty()
        succeeds('installDist')

        when:
        runViaUnixStartScript("bash")

        then:
        outputContains("App Home: ${file('build/install/sample').absolutePath}")
    }

    @Requires([UnitTestPreconditions.UnixDerivative, PluginTestPreconditions.DashAvailable])
    def "can use APP_HOME in DEFAULT_JVM_OPTS with custom start script in Dash"() {
        given:
        extendBuildFileWithAppHomeProperty()
        succeeds('installDist')

        when:
        runViaUnixStartScript("dash")

        then:
        outputContains("App Home: ${file('build/install/sample').absolutePath}")
    }

    @Requires([UnitTestPreconditions.UnixDerivative, PluginTestPreconditions.StaticShAvailable])
    def "can use APP_HOME in DEFAULT_JVM_OPTS with custom start script in BusyBox"() {
        given:
        extendBuildFileWithAppHomeProperty()
        succeeds('installDist')

        when:
        runViaUnixStartScript("static-sh")

        then:
        outputContains("App Home: ${file('build/install/sample').absolutePath}")
    }

    @Requires([UnitTestPreconditions.UnixDerivative, PluginTestPreconditions.BashAvailable])
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

    @Requires([UnitTestPreconditions.UnixDerivative, PluginTestPreconditions.DashAvailable])
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

    @Requires([UnitTestPreconditions.UnixDerivative, PluginTestPreconditions.StaticShAvailable])
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

    @Requires([UnitTestPreconditions.Jdk9OrLater, PluginTestPreconditions.BashAvailable])
    def "can execute generated Unix start script for Java module in Bash"() {
        given:
        turnSampleProjectIntoModule()
        succeeds('installDist')

        when:
        runViaUnixStartScript("bash")

        then:
        outputContains('Hello World!')
    }

    @Requires([UnitTestPreconditions.Jdk9OrLater, PluginTestPreconditions.DashAvailable])
    def "can execute generated Unix start script for Java module in Dash"() {
        given:
        turnSampleProjectIntoModule()
        succeeds('installDist')

        when:
        runViaUnixStartScript("dash")

        then:
        outputContains('Hello World!')
    }

    @Requires([UnitTestPreconditions.Jdk9OrLater, PluginTestPreconditions.StaticShAvailable])
    def "can execute generated Unix start script for Java module in BusyBox"() {
        given:
        turnSampleProjectIntoModule()
        succeeds('installDist')

        when:
        runViaUnixStartScript("static-sh")

        then:
        outputContains('Hello World!')
    }

    @Requires(PluginTestPreconditions.ShellcheckAvailable)
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
    commandLine '${PluginTestPreconditions.locate(shCommand).absolutePath}'
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
        unixScript.asFile.get().text = unixScript.asFile.get().text.replace("REPLACE_THIS_WITH_APP_HOME", "'\\\$APP_HOME'")
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
