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
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.PluginTestPreconditions
import org.gradle.test.preconditions.OsTestPreconditions
import org.gradle.test.preconditions.JdkVersionTestPreconditions
import spock.lang.Issue

import java.util.jar.JarOutputStream


class ApplicationPluginUnixShellsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        createSampleProjectSetup()
    }

    def cleanup() {
        if (testDirectoryProvider.cleanup) {
            testDirectory.usingNativeTools().deleteDir() //remove symlinks
        }
    }

    @Requires([OsTestPreconditions.Unix, PluginTestPreconditions.BashAvailable])
    def "can execute generated Unix start script in Bash"() {
        given:
        succeeds('installDist')

        when:
        runViaUnixStartScript("bash")

        then:
        outputContains('Hello World!')
    }

    @Issue("https://github.com/gradle/gradle/issues/25721")
    @Requires([OsTestPreconditions.Unix, PluginTestPreconditions.BashAvailable])
    def "can execute generated Unix start script with a Java executable extension"() {
        given:
        def javaHome = file("windows-java-home")
        javaHome.file("bin/java.exe").createLink(Jvm.current().javaExecutable)
        succeeds('installDist')

        when:
        runViaUnixStartScriptWithJavaHome("bash", javaHome.absolutePath)

        then:
        outputContains('Hello World!')
    }

    @Issue("https://github.com/gradle/gradle/issues/25721")
    @Requires([OsTestPreconditions.Unix, PluginTestPreconditions.BashAvailable])
    def "converts #pathOption path list when running Windows Java from WSL"() {
        given:
        def javaArgs = file("java-args.txt")
        def wslpathCalls = file("wslpath-calls.txt")
        def javaHome = file("windows-java-home")
        def javaExecutable = javaHome.file("bin/java.exe") << """#!/bin/sh
printf '%s\\n' "\$@" > '${javaArgs.absolutePath}'
"""
        javaExecutable.setExecutable(true)

        def windowsInteropBin = file("windows-interop-bin")
        def wslpath = windowsInteropBin.file("wslpath") << """#!/bin/sh
[ "\$1" = "-m" ] || exit 1
printf '%s\\n' "\$2" >> '${wslpathCalls.absolutePath}'
printf 'windows/%s\\n' "\$2"
"""
        wslpath.setExecutable(true)

        file("libs").createDir()
        new JarOutputStream(file("libs/dependency.jar").newOutputStream()).close()
        buildFile << """
dependencies {
    runtimeOnly files('libs/dependency.jar')
}
"""
        if (modular) {
            turnSampleProjectIntoModule()
        }
        succeeds('installDist')

        when:
        runViaUnixStartScriptWithJavaHome("bash", javaHome.absolutePath, [
            PATH: "${windowsInteropBin.absolutePath}:${System.getenv('PATH')}",
            WSL_DISTRO_NAME: "test-distro"
        ])

        then:
        wslpathCalls.readLines().first() == file('build/install/sample').absolutePath
        def args = javaArgs.readLines()
        def pathOptionIndex = args.indexOf(pathOption)
        pathOptionIndex >= 0
        def convertedPaths = args[pathOptionIndex + 1].split(';')
        convertedPaths.size() == expectedPathCount
        convertedPaths.every { it.startsWith('windows/') }
        convertedPaths.any { it.endsWith('/sample.jar') }
        convertedPaths.any { it.endsWith('/dependency.jar') } == !modular

        where:
        modular | pathOption      | expectedPathCount
        false   | '-classpath'    | 2
        true    | '--module-path' | 1
    }

    @Requires([OsTestPreconditions.Unix, PluginTestPreconditions.DashAvailable])
    def "can execute generated Unix start script in Dash"() {
        given:
        succeeds('installDist')

        when:
        runViaUnixStartScript("dash")

        then:
        outputContains('Hello World!')
    }

    @Requires([OsTestPreconditions.Unix, PluginTestPreconditions.StaticShAvailable])
    def "can execute generated Unix start script in BusyBox"() {
        given:
        succeeds('installDist')

        when:
        runViaUnixStartScript("static-sh")

        then:
        outputContains('Hello World!')
    }

    @Requires([OsTestPreconditions.Unix, PluginTestPreconditions.BashAvailable])
    def "can use APP_HOME in DEFAULT_JVM_OPTS with custom start script in Bash"() {
        given:
        extendBuildFileWithAppHomeProperty()
        succeeds('installDist')

        when:
        runViaUnixStartScript("bash")

        then:
        outputContains("App Home: ${file('build/install/sample').absolutePath}")
    }

    @Requires([OsTestPreconditions.Unix, PluginTestPreconditions.DashAvailable])
    def "can use APP_HOME in DEFAULT_JVM_OPTS with custom start script in Dash"() {
        given:
        extendBuildFileWithAppHomeProperty()
        succeeds('installDist')

        when:
        runViaUnixStartScript("dash")

        then:
        outputContains("App Home: ${file('build/install/sample').absolutePath}")
    }

    @Requires([OsTestPreconditions.Unix, PluginTestPreconditions.StaticShAvailable])
    def "can use APP_HOME in DEFAULT_JVM_OPTS with custom start script in BusyBox"() {
        given:
        extendBuildFileWithAppHomeProperty()
        succeeds('installDist')

        when:
        runViaUnixStartScript("static-sh")

        then:
        outputContains("App Home: ${file('build/install/sample').absolutePath}")
    }

    @Requires([OsTestPreconditions.Unix, PluginTestPreconditions.BashAvailable])
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

    @Requires([OsTestPreconditions.Unix, PluginTestPreconditions.DashAvailable])
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

    @Requires([OsTestPreconditions.Unix, PluginTestPreconditions.StaticShAvailable])
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

    @Requires([JdkVersionTestPreconditions.Jdk9OrLater, PluginTestPreconditions.BashAvailable])
    def "can execute generated Unix start script for Java module in Bash"() {
        given:
        turnSampleProjectIntoModule()
        succeeds('installDist')

        when:
        runViaUnixStartScript("bash")

        then:
        outputContains('Hello World!')
    }

    @Requires([JdkVersionTestPreconditions.Jdk9OrLater, PluginTestPreconditions.DashAvailable])
    def "can execute generated Unix start script for Java module in Dash"() {
        given:
        turnSampleProjectIntoModule()
        succeeds('installDist')

        when:
        runViaUnixStartScript("dash")

        then:
        outputContains('Hello World!')
    }

    @Requires([JdkVersionTestPreconditions.Jdk9OrLater, PluginTestPreconditions.StaticShAvailable])
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

    ExecutionResult runViaUnixStartScriptWithJavaHome(String shCommand, String javaHome, Map<String, String> additionalEnvironment = [:]) {
        TestFile startScriptDir = file('build/install/sample/bin')
        String additionalEnvironmentSetup = additionalEnvironment.collect { name, value ->
            "    environment('${name}', '${value}')"
        }.join('\n')
        buildFile << """
task execStartScript(type: Exec) {
    workingDir '$startScriptDir.canonicalPath'
    commandLine '${PluginTestPreconditions.locate(shCommand).absolutePath}'
    args "./sample"
    environment JAVA_HOME: "$javaHome"
    environment.keySet().removeIf { it.equalsIgnoreCase('WSL_DISTRO_NAME') }
$additionalEnvironmentSetup
}
"""
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
