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
import org.gradle.integtests.fixtures.TestResources
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.app.CHelloWorldApp
import org.junit.Rule

import static org.gradle.util.TextUtil.normaliseLineSeparators

// TODO:DAZ Test up-to-date checks
class CUnitIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @Rule TestResources resources = new TestResources(temporaryFolder)
    def app = new CHelloWorldApp()

    def setup() {
        buildFile << """
            apply plugin: "c"
            apply plugin: "cunit"

            model {
                repositories {
                    libs(PrebuiltLibraries) {
                        cunit {
                            headers.srcDir "libs/cunit/2.1-2/include"
                            binaries.withType(StaticLibraryBinary) {
                                staticLibraryFile = file("libs/cunit/2.1-2/lib/${cunitPlatform}/${cunitLibName}")
                            }
                        }
                    }
                }
            }

            libraries {
                hello {}
            }
            binaries.withType(TestSuiteExecutableBinary) {
                lib library: "cunit", linkage: "static"
            }
        """
        settingsFile << "rootProject.name = 'test'"

        app.library.writeSources(file("src/hello"))
        app.cunitTests.writeSources(file("src/helloTest"))
    }

    private def getCunitPlatform() {
        if (OperatingSystem.current().isMacOsX()) {
            return "osx"
        }
        if (OperatingSystem.current().isLinux()) {
            return "linux"
        }
        if (toolChain.displayName == "mingw") {
            return "mingw"
        }
        if (toolChain.displayName == "gcc cygwin") {
            return "cygwin"
        }
        return "win"
    }

    private def getCunitLibName() {
        return OperatingSystem.current().getStaticLibraryName("cunit")
    }

    def "can build and run cunit test suite"() {
        when:
        run "runHelloTestCUnitExe"

        then:
        executedAndNotSkipped ":compileHelloTestCUnitExeHelloC", ":compileHelloTestCUnitExeHelloTestCunit",
                              ":linkHelloTestCUnitExe", ":helloTestCUnitExe", ":runHelloTestCUnitExe"
        file("build/test-results/helloTestCUnitExe/CUnitAutomated-Results.xml").assertExists()
        file("build/test-results/helloTestCUnitExe/CUnitAutomated-Listing.xml").assertExists()
        // TODO:DAZ Verify the generated xml
    }

    def "can build and run cunit failing test suite"() {
        when:
        file("src/hello/c/sum.c").text = file("src/hello/c/sum.c").text.replace("return a + b;", "return a - b;")
        fails "runHelloTestCUnitExe"

        then:
        executedAndNotSkipped ":compileHelloTestCUnitExeHelloC", ":compileHelloTestCUnitExeHelloTestCunit",
                              ":linkHelloTestCUnitExe", ":helloTestCUnitExe", ":runHelloTestCUnitExe"

        output.contains """
There were test failures:
"""
        file("build/test-results/helloTestCUnitExe/CUnitAutomated-Results.xml").assertExists()
        file("build/test-results/helloTestCUnitExe/CUnitAutomated-Listing.xml").assertExists()
        // TODO:DAZ Verify the failure message: should include useful error and link to results file
        // TODO:DAZ Verify the generated xml
    }

    @Override
    String getOutput() {
        return normaliseLineSeparators(super.getOutput())
    }
}
