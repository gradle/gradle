/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.tooling.r43

import org.gradle.integtests.fixtures.ScriptExecuter
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.r18.NullAction
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.util.GradleVersion

abstract class ToolingApiVersionSupport extends ToolingApiSpecification {
    def output = new ByteArrayOutputStream()

    def setup() {
        file("build.gradle") << noopScript()
    }

    def noopScript() {
        """
task noop {
    doLast {
        println "noop"
    }
}
"""
    }

    // AbstractConsumerConnection.getVersionDetail was introduced in 1.2
    def minProviderVersionDetail = GradleVersion.version('1.1')

    def currentVersionMessage(GradleVersion version, GradleVersion lowerBound) {
        if (version >= lowerBound) {
            return "You are currently using ${version.version}. "
        } else {
            return ''
        }
    }
    def currentProviderMessage(String version) {
        return currentVersionMessage(GradleVersion.version(version), minProviderVersionDetail)
    }

    String providerUnsupportedMessage(String version) {
        return "Support for Gradle older than 1.2 has been removed. ${currentProviderMessage(version)}You should upgrade your Gradle to version 1.2 or later."
    }

    String providerDeprecationMessage(String version) {
        return "Support for Gradle older than 2.6 is deprecated and will be removed in 5.0. You are currently using ${version}. You should upgrade your Gradle."
    }

    // since 1.0
    def build() {
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardOutput = output
            build.run()
        }
    }
    def buildViaScript() {
        packageExecutable('.newBuild().forTasks("noop").run()')
        buildFile.write(noopScript())
        return runScript()
    }

    // since 1.0
    def getModel() {
        withConnection { ProjectConnection connection ->
            def model = connection.model(EclipseProject)
            model.standardOutput = output
            model.get()
        }
    }
    def getModelViaScript() {
        packageExecutable('.model(org.gradle.tooling.model.eclipse.EclipseProject.class).get()')
        buildFile.write(noopScript())
        return runScript()
    }

    // since 1.8
    def buildAction() {
        withConnection { ProjectConnection connection ->
            def action = connection.action(new NullAction())
            action.standardOutput = output
            action.run()
        }
    }

    def buildActionViaScript() {
        packageExecutable('.action(new org.gradle.tooling.BuildAction(){public Object execute(org.gradle.tooling.BuildController controller) {return null;}}).run()')
        buildFile.write(noopScript())
        return runScript()
    }

    // since 2.6
    def testExecution() {
        withConnection { ProjectConnection connection ->
            def launcher = connection.newTestLauncher().withJvmTestClasses("class")
            launcher.standardOutput = output
            launcher.run()
        }
    }

    def testExecutionViaScript() {
        packageExecutable('.newTestLauncher().withJvmTestClasses("TestClientTest").run()')
        buildFile.write""" 
apply plugin: 'java'
repositories {
    maven {
        url '${buildContext.libsRepo.toURI()}'
    }
    maven {
        url 'https://repo.gradle.org/gradle/libs-releases-local'
    }
}
${mavenCentralRepository()}
dependencies {
    testCompile 'junit:junit:4.12'
}
"""
        file('src/main').deleteDir()
        file('src/test/java/TestClientTest.java') << '''
public class TestClientTest{
    @org.junit.Test public void test(){
    }
}'''
        return runScript()
    }

    def packageExecutable(String operation) {
        settingsFile << "rootProject.name = 'test'"

        buildFile << """
apply plugin: 'application'
sourceCompatibility = 1.6
targetCompatibility = 1.6
repositories {
    maven {
        url '${buildContext.libsRepo.toURI()}'
    }
    maven {
        url 'https://repo.gradle.org/gradle/libs-releases-local'
    }
}
${mavenCentralRepository()}
dependencies {
    compile "org.gradle:gradle-tooling-api:${GradleVersion.current().version}"
    runtime 'org.slf4j:slf4j-simple:1.7.10'
}
mainClassName = 'TestClient'
"""
        file('src/main/java/TestClient.java') << """
import org.gradle.tooling.GradleConnector;
import java.io.File;
public class TestClient {
    public static void main(String[] args) {
        GradleConnector
            .newConnector()
            .forProjectDirectory(new File("${projectDir.toString().replace('\\', '/')}"))
            .useInstallation(new File("${targetDist.gradleHomeDir.toString().replace('\\', '/')}"))
            .connect()
            ${operation};
    }
}
"""

        dist.executer(temporaryFolder, buildContext).inDirectory(projectDir).withTasks("installDist").run()
    }

    String runScript() {
        def stdout = new ByteArrayOutputStream()
        def executer = new ScriptExecuter()
        executer.workingDir(projectDir)
        executer.standardOutput = stdout
        executer.commandLine("build/install/test/bin/test")
        executer.run().assertNormalExitValue()

        return stdout.toString()
    }
}
