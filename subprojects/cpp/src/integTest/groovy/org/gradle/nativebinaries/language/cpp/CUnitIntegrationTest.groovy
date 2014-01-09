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

import org.gradle.ide.visualstudio.fixtures.ProjectFile
import org.gradle.ide.visualstudio.fixtures.SolutionFile
import org.gradle.integtests.fixtures.TestResources
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.AvailableToolChains
import org.gradle.nativebinaries.language.cpp.fixtures.app.CHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

import static org.gradle.util.TextUtil.normaliseLineSeparators

@Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)

// TODO:DAZ Test incremental
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
        if (toolChain.visualCpp) {
            def vcVersion = (toolChain as AvailableToolChains.InstalledVisualCpp).version
            switch (vcVersion.major) {
                case "12":
                    return "vs2013"
                case "10":
                    return "vs2010"
            }
        }
        throw new IllegalStateException("No cunit binary available for ${toolChain.displayName}")
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

    def "creates visual studio solution and project for cunit test suite"() {
        buildFile.text = "apply plugin: 'visual-studio'\n" + buildFile.text

        when:
        succeeds "helloTestVisualStudio"

        then:
        final mainSolution = new SolutionFile(file("visualStudio/helloTestExe.sln"))
        mainSolution.assertHasProjects("helloTestExe")

        and:
        final projectFile = new ProjectFile(file("visualStudio/helloTestExe.vcxproj"))
        projectFile.sourceFiles as Set == [
                file("src/helloTest/cunit/test.c"),
                file("build/src/cunitLauncher/gradle_cunit_main.c"),
                file("build/src/cunitLauncher/gradle_cunit_register.h"),
                file("src/hello/c/hello.c"),
                file("src/hello/c/sum.c")
        ]*.absolutePath as Set
        projectFile.headerFiles == [
                file("src/hello/headers/hello.h")
        ]*.absolutePath
        projectFile.projectConfigurations.keySet() == ['debug|Win32'] as Set
        with (projectFile.projectConfigurations['debug|Win32']) {
            includePath == [file("src/hello/headers"), file("libs/cunit/2.1-2/include")]*.absolutePath.join(';')
        }
    }

    @Override
    String getOutput() {
        return normaliseLineSeparators(super.getOutput())
    }
}
