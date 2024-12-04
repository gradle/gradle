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

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.r18.NullAction
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.eclipse.EclipseProject

abstract class ToolingApiVersionSpecification extends ToolingApiSpecification {
    def outputStream

    String providerDeprecationMessage(String version) {
        return "You are currently using Gradle version ${version}. You should upgrade your Gradle build to use Gradle 2.6 or later."
    }

    String consumerDeprecationMessage(String version) {
        return "You are currently using tooling API version ${version}. You should upgrade your tooling API client to version 3.0 or later"
    }

    def setup() {
        outputStream = new ByteArrayOutputStream()
    }

    def getOutput() {
        outputStream.toString()
    }

    // since 1.0
    def build() {
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardOutput = outputStream
            build.run()
        }
    }

    // since 1.0
    def getModel() {
        withConnection { ProjectConnection connection ->
            def model = connection.model(EclipseProject)
            model.standardOutput = outputStream
            model.get()
        }
    }

    // since 1.8
    def buildAction() {
        withConnection { ProjectConnection connection ->
            def action = connection.action(new NullAction())
            action.standardOutput = outputStream
            action.run()
        }
    }

    // since 2.6
    def testExecution() {
        buildFile << """
apply plugin: 'java'
repositories {
    maven {
        url = '${buildContext.localRepository.toURI()}'
    }
}
${mavenCentralRepository()}
dependencies {
    testImplementation 'junit:junit:4.13'
}
"""
        file('src/test/java/TestClientTest.java') << '''
public class TestClientTest{
    @org.junit.Test public void test(){
    }
}'''
        withConnection { ProjectConnection connection ->
            def launcher = connection.newTestLauncher().withJvmTestClasses("TestClientTest")
            launcher.standardOutput = outputStream
            launcher.run()
        }
    }

    // since 6.1
    def notifyDaemonsAboutChangedPaths() {
        build()

        withConnection { ProjectConnection connection ->
            connection.notifyDaemonsAboutChangedPaths([file("some/file").toPath()])
        }
    }
}
