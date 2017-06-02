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

package org.gradle.language.swift

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.ExecutableFixture
import org.gradle.nativeplatform.fixtures.app.HelloWorldApp
import org.gradle.nativeplatform.fixtures.app.SwiftCompilerDetectingTestApp
import org.gradle.nativeplatform.fixtures.app.SwiftHelloWorldApp
import org.gradle.nativeplatform.toolchain.plugins.SwiftCompilerPlugin
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.util.Matchers.containsText

class SwiftExecutableIntegrationTest extends AbstractIntegrationSpec {
    def helloWorldApp = new SwiftHelloWorldApp()
    File initScript

    def setup() {
        initScript = file("init.gradle") << """
allprojects { p ->
    apply plugin: ${SwiftCompilerPlugin.simpleName}

    model {
          toolChains {
            swiftc(Swiftc)
          }
    }
}
"""
        executer.beforeExecute({
            usingInitScript(initScript)
        })
    }

    def "build fails when compilation fails"() {
        given:
        buildFile << """
            apply plugin: 'swift-executable'
         """

        and:
        helloWorldApp.brokenFile.writeToDir(file("src/main"))

        expect:
        fails "assemble"
        failure.assertHasDescription("Execution failed for task ':compileSwift'.");
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(containsText("Swift compiler failed while compiling swift file(s)"))
    }

    def "sources are compiled with Swift compiler"() {
        given:
        helloWorldApp.writeSources(file('src/main'))

        and:
        buildFile << """
            apply plugin: 'swift-executable'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileSwift", ":assemble")
        executable("build/exe/main").exec().out == "Hello, World!\n12\n"//app.expectedOutput(AbstractInstalledToolChainIntegrationSpec.toolChain)
    }

    def ExecutableFixture executable(Object path) {
        return new AvailableToolChains.InstalledSwiftc().executable(file(path));
    }

}
