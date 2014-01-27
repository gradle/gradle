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
import org.gradle.nativebinaries.test.cunit.CUnitTestResults
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

import static org.gradle.util.TextUtil.normaliseLineSeparators

@Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
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
        file("build/test-results/helloTestCUnitExe/CUnitAutomated-Listing.xml").assertExists()

        def testResults = new CUnitTestResults(file("build/test-results/helloTestCUnitExe/CUnitAutomated-Results.xml"))
        testResults.suiteNames == ['hello test']
        testResults.suites['hello test'].passingTests == ['test_sum']
        testResults.suites['hello test'].failingTests == []
        testResults.checkTestCases(1, 1, 0)
        testResults.checkAssertions(3, 3, 0)
    }

    def "test suite skipped after successful run"() {
        given:
        run "runHelloTestCUnitExe"

        when:
        run "runHelloTestCUnitExe"

        then:
        skipped ":helloTestCUnitExe", ":runHelloTestCUnitExe"
    }

    def "can build and run cunit failing test suite"() {
        when:
        useFailingSources()
        fails "runHelloTestCUnitExe"

        then:
        failure.assertHasDescription("Execution failed for task ':runHelloTestCUnitExe'.")
        failure.assertHasCause("There were failing tests. See the results at: ")

        and:
        executedAndNotSkipped ":compileHelloTestCUnitExeHelloC", ":compileHelloTestCUnitExeHelloTestCunit",
                              ":linkHelloTestCUnitExe", ":helloTestCUnitExe", ":runHelloTestCUnitExe"
        output.contains """
There were test failures:
"""
        and:
        def testResults = new CUnitTestResults(file("build/test-results/helloTestCUnitExe/CUnitAutomated-Results.xml"))
        testResults.suiteNames == ['hello test']
        testResults.suites['hello test'].passingTests == []
        testResults.suites['hello test'].failingTests == ['test_sum']
        testResults.checkTestCases(1, 0, 1)
        testResults.checkAssertions(3, 1, 2)
        file("build/test-results/helloTestCUnitExe/CUnitAutomated-Listing.xml").assertExists()
    }


    def "build does not break for failing tests if ignoreFailures is true"() {
        when:
        useFailingSources()
        buildFile << """
    tasks.withType(RunTestExecutable) {
        it.ignoreFailures = true
    }
"""
        succeeds "runHelloTestCUnitExe"

        then:
        output.contains """
There were test failures:
"""
        output.contains "There were failing tests. See the results at: "

        and:
        file("build/test-results/helloTestCUnitExe/CUnitAutomated-Results.xml").assertExists()
        file("build/test-results/helloTestCUnitExe/CUnitAutomated-Listing.xml").assertExists()
    }

    def "test suite not skipped after failing run"() {
        given:
        final String originalText = file("src/hello/c/sum.c").text
        file("src/hello/c/sum.c").text = originalText.replace("return a + b;", "return 2;")
        fails "runHelloTestCUnitExe"

        when:
        fails "runHelloTestCUnitExe"

        then:
        executedAndNotSkipped ":runHelloTestCUnitExe"
    }

    def "creates visual studio solution and project for cunit test suite"() {
        buildFile.text = "apply plugin: 'visual-studio'\n" + buildFile.text

        when:
        succeeds "helloTestVisualStudio"

        then:
        final mainSolution = new SolutionFile(file("helloTestExe.sln"))
        mainSolution.assertHasProjects("helloTestExe")

        and:
        final projectFile = new ProjectFile(file("helloTestExe.vcxproj"))
        projectFile.sourceFiles as Set == [
                "src/helloTest/cunit/test.c",
                "build/src/cunitLauncher/gradle_cunit_main.c",
                "build/src/cunitLauncher/gradle_cunit_register.h",
                "src/hello/c/hello.c",
                "src/hello/c/sum.c"
        ] as Set
        projectFile.headerFiles == ["src/hello/headers/hello.h"]
        projectFile.projectConfigurations.keySet() == ['debug'] as Set
        with (projectFile.projectConfigurations['debug']) {
            includePath == "src/hello/headers;libs/cunit/2.1-2/include"
        }
    }

    private def useFailingSources() {
        file("src/hello/c/sum.c").text = file("src/hello/c/sum.c").text.replace("return a + b;", "return 2;")
    }

    @Override
    String getOutput() {
        return normaliseLineSeparators(super.getOutput())
    }
}
