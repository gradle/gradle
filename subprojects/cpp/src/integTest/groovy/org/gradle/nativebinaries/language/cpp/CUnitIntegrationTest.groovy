/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativebinaries.language.cpp
import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.app.CHelloWorldApp

// TODO:DAZ Add unit tests to TestApp and use it here
class CUnitIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def app = new CHelloWorldApp()

    def setup() {
        buildFile << """
            apply plugin: "c"
            apply plugin: "cunit"

            libraries {
                hello {}
            }
            binaries.withType(TestSuiteExecutableBinary) {
                linker.args "-lcunit"
            }
        """
        settingsFile << "rootProject.name = 'test'"

        app.library.writeSources(file("src/hello"))
        app.cunitTests.writeSources(file("src/helloTest"))
    }

    def "can build and run cunit test suite"() {
        when:
        run "runHelloTestCUnitExe"

        then:
        executedAndNotSkipped ":compileHelloTestCUnitExeHelloC", ":compileHelloTestCUnitExeHelloTestCunit",
                              ":linkHelloTestCUnitExe", ":helloTestCUnitExe", ":runHelloTestCUnitExe"
        output.contains app.cunitTests.testOutput
    }

    def "can build and run cunit failing test suite"() {
        when:
        file("src/hello/c/sum.c").text = file("src/hello/c/sum.c").text.replace("return a + b;", "return a - b;")
        fails "runHelloTestCUnitExe"

        then:
        executedAndNotSkipped ":compileHelloTestCUnitExeHelloC", ":compileHelloTestCUnitExeHelloTestCunit",
                              ":linkHelloTestCUnitExe", ":helloTestCUnitExe", ":runHelloTestCUnitExe"
    }
}
