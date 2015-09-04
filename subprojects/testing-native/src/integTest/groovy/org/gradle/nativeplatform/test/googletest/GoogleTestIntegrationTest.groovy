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
package org.gradle.nativeplatform.test.googletest

import org.gradle.ide.visualstudio.fixtures.ProjectFile
import org.gradle.ide.visualstudio.fixtures.SolutionFile
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TextUtil
import spock.lang.Issue

import static org.gradle.util.TextUtil.normaliseLineSeparators

@Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
@LeaksFileHandles
class GoogleTestIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def prebuiltPath = TextUtil.normaliseFileSeparators(new IntegrationTestBuildContext().getSamplesDir().file("native-binaries/google-test/libs").path)
    def app = new CppHelloWorldApp()

    def setup() {
        buildFile << """
apply plugin: "google-test"

model {
    repositories {
        libs(PrebuiltLibraries) {
            googleTest {
                headers.srcDir "${prebuiltPath}/googleTest/1.7.0/include"
                binaries.withType(StaticLibraryBinary) {
                    staticLibraryFile = file("${prebuiltPath}/googleTest/1.7.0/lib/${googleTestPlatform}/${googleTestLib}")
                }
            }
        }
    }
    platforms {
        x86 {
            architecture "x86"
        }
    }
}
"""
        settingsFile << "rootProject.name = 'test'"
    }

    private void useStandardConfig() {
        buildFile << """
model {
    components {
        hello(NativeLibrarySpec) {
            targetPlatform "x86"
        }
    }
}
tasks.withType(RunTestExecutable) {
    args "--gtest_output=xml:test_detail.xml"
}
"""
        addGoogleTestDep()
    }

    private void addGoogleTestDep() {
        buildFile << """
binaries.withType(GoogleTestTestSuiteBinarySpec) {
    lib library: "googleTest", linkage: "static"
    if (targetPlatform.operatingSystem.linux) {
        cppCompiler.args '-pthread'
        linker.args '-pthread'
    }
}
"""
    }

    private def getGoogleTestPlatform() {
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
        throw new IllegalStateException("No googletest binary available for ${toolChain.displayName}")
    }

    private def getGoogleTestLib() {
        return OperatingSystem.current().getStaticLibraryName("gtest")
    }

    def "can build and run googleTest test suite"() {
        given:
        useConventionalSourceLocations()
        useStandardConfig()

        when:
        run "runHelloTestGoogleTestExe"

        then:
        executedAndNotSkipped ":compileHelloTestGoogleTestExeHelloCpp", ":compileHelloTestGoogleTestExeHelloTestCpp",
                ":linkHelloTestGoogleTestExe", ":helloTestGoogleTestExe", ":runHelloTestGoogleTestExe"

        def testResults = new GoogleTestTestResults(file("build/test-results/helloTestGoogleTestExe/test_detail.xml"))
        testResults.suiteNames == ['HelloTest']
        testResults.suites['HelloTest'].passingTests == ['test_sum']
        testResults.suites['HelloTest'].failingTests == []
        testResults.checkTestCases(1, 1, 0)
    }

    @Issue("GRADLE-3225")
    def "can build and run googleTest test suite with C and C++ plugins"() {
        given:
        useConventionalSourceLocations()
        useStandardConfig()
        buildFile << "apply plugin: 'c'"
        file("src/hello/c").createDir().file("foo.c").text = "int foobar() { return 0; }"

        when:
        run "runHelloTestGoogleTestExe"

        then:
        executedAndNotSkipped ":compileHelloTestGoogleTestExeHelloCpp", ":compileHelloTestGoogleTestExeHelloC",
            ":compileHelloTestGoogleTestExeHelloTestCpp",
            ":linkHelloTestGoogleTestExe", ":helloTestGoogleTestExe", ":runHelloTestGoogleTestExe"
    }

    def "can configure via testSuite component"() {
        given:
        useConventionalSourceLocations()

        buildFile << """
model {
    components {
        hello(NativeLibrarySpec) {
            targetPlatform "x86"
        }
    }
    testSuites {
        helloTest {
            binaries.all {
                lib library: "googleTest", linkage: "static"
            }
        }
    }
}

tasks.withType(RunTestExecutable) {
    args "--gtest_output=xml:test_detail.xml"
}
"""
        addGoogleTestDep()

        when:
        run "runHelloTestGoogleTestExe"

        then:
        executedAndNotSkipped ":compileHelloTestGoogleTestExeHelloCpp", ":compileHelloTestGoogleTestExeHelloTestCpp",
                ":linkHelloTestGoogleTestExe", ":helloTestGoogleTestExe", ":runHelloTestGoogleTestExe"

        def testResults = new GoogleTestTestResults(file("build/test-results/helloTestGoogleTestExe/test_detail.xml"))
        testResults.suiteNames == ['HelloTest']
        testResults.suites['HelloTest'].passingTests == ['test_sum']
        testResults.suites['HelloTest'].failingTests == []
        testResults.checkTestCases(1, 1, 0)
    }

    def "can supply cppCompiler macro to googleTest sources"() {
        given:
        useConventionalSourceLocations()
        useStandardConfig()

        when:
        buildFile << """
binaries.withType(GoogleTestTestSuiteBinarySpec) {
    cppCompiler.define "ONE_TEST"
}
"""
        and:
        run "runHelloTestGoogleTestExe"

        then:
        def testResults = new GoogleTestTestResults(file("build/test-results/helloTestGoogleTestExe/test_detail.xml"))
        testResults.checkTestCases(1, 1, 0)
    }

    def "can configure location of googleTest test sources"() {
        given:
        useStandardConfig()
        app.library.writeSources(file("src/hello"))
        app.googleTestTests.writeSources(file("src/alternateHelloTest"))

        when:
        buildFile << """
model {
    testSuites {
        helloTest {
            sources {
                cpp {
                    source.srcDir "src/alternateHelloTest/cpp"
                }
            }
        }
    }
}
"""

        then:
        succeeds "runHelloTestGoogleTestExe"
    }

    def "can configure location of googleTest test sources before component is declared"() {
        given:
        app.library.writeSources(file("src/hello"))
        app.googleTestTests.writeSources(file("src/alternateHelloTest"))

        when:
        buildFile << """
model {
    testSuites {
        helloTest {
            sources {
                cpp {
                    source.srcDir "src/alternateHelloTest/cpp"
                }
            }
        }
    }
}
"""
        useStandardConfig()

        then:
        succeeds "runHelloTestGoogleTestExe"
    }

    def "variant-dependent sources are included in test binary"() {
        given:
        app.library.headerFiles*.writeToDir(file("src/hello"))
        app.googleTestTests.writeSources(file("src/helloTest"))
        app.library.sourceFiles*.writeToDir(file("src/variant"))

        when:
        buildFile << """
model {
    components {
        hello(NativeLibrarySpec) {
            targetPlatform "x86"
            binaries.all {
                sources {
                    variant(CppSourceSet) {
                        source.srcDir "src/variant/cpp"
                        lib hello.sources.cpp
                    }
                }
            }
        }
    }
}
"""
        addGoogleTestDep()

        then:
        succeeds "runHelloTestGoogleTestExe"
    }

    def "can configure variant-dependent test sources"() {
        given:
        useStandardConfig()
        app.library.writeSources(file("src/hello"))
        app.googleTestTests.writeSources(file("src/variantTest"))

        when:
        buildFile << """
model {
    testSuites {
        helloTest {
            binaries.all {
                sources {
                    variant(CppSourceSet) {
                        source.srcDir "src/variantTest/cpp"
                        lib helloTest.sources.cpp
                    }
                }
            }
        }
    }
}
"""

        then:
        succeeds "runHelloTestGoogleTestExe"
    }

    def "test suite skipped after successful run"() {
        given:
        useStandardConfig()
        useConventionalSourceLocations()
        run "runHelloTestGoogleTestExe"

        when:
        run "runHelloTestGoogleTestExe"

        then:
        skipped ":helloTestGoogleTestExe", ":runHelloTestGoogleTestExe"
    }

    def "can build and run googleTest failing test suite"() {
        when:
        useStandardConfig()
        useFailingTestSources()
        fails "runHelloTestGoogleTestExe"

        then:
        failure.assertHasDescription("Execution failed for task ':runHelloTestGoogleTestExe'.")
        failure.assertHasCause("There were failing tests. See the results at: ")

        and:
        executedAndNotSkipped ":compileHelloTestGoogleTestExeHelloCpp", ":compileHelloTestGoogleTestExeHelloTestCpp",
                ":linkHelloTestGoogleTestExe", ":helloTestGoogleTestExe", ":runHelloTestGoogleTestExe"
        output.contains "[  FAILED  ]"
        and:
        def testResults = new GoogleTestTestResults(file("build/test-results/helloTestGoogleTestExe/test_detail.xml"))
        testResults.suiteNames == ['HelloTest']
        testResults.suites['HelloTest'].passingTests == []
        testResults.suites['HelloTest'].failingTests == ['test_sum']
        testResults.checkTestCases(1, 0, 1)
    }

    def "build does not break for failing tests if ignoreFailures is true"() {
        when:
        useStandardConfig()
        useFailingTestSources()
        buildFile << """
tasks.withType(RunTestExecutable) {
    it.ignoreFailures = true
}
"""
        succeeds "runHelloTestGoogleTestExe"

        then:
        output.contains "[  FAILED  ] "
        output.contains "There were failing tests. See the results at: "

        and:
        file("build/test-results/helloTestGoogleTestExe/test_detail.xml").assertExists()
    }

    def "test suite not skipped after failing run"() {
        given:
        useStandardConfig()
        useFailingTestSources()
        fails "runHelloTestGoogleTestExe"

        when:
        fails "runHelloTestGoogleTestExe"

        then:
        executedAndNotSkipped ":runHelloTestGoogleTestExe"
    }

    def "creates visual studio solution and project for googleTest test suite"() {
        given:
        useStandardConfig()
        useConventionalSourceLocations()
        buildFile.text = "apply plugin: 'visual-studio'\n" + buildFile.text

        when:
        succeeds "helloTestVisualStudio"

        then:
        final mainSolution = new SolutionFile(file("helloTestExe.sln"))
        mainSolution.assertHasProjects("helloTestExe")

        and:
        final projectFile = new ProjectFile(file("helloTestExe.vcxproj"))
        projectFile.sourceFiles as Set == [
                "build.gradle",
                "src/helloTest/cpp/test.cpp",
                "src/hello/cpp/hello.cpp",
                "src/hello/cpp/sum.cpp"
        ] as Set
        projectFile.headerFiles == [
                "src/hello/headers/common.h",
                "src/hello/headers/hello.h"
        ]
        projectFile.projectConfigurations.keySet() == ['debug'] as Set
        with (projectFile.projectConfigurations['debug']) {
            includePath == "src/helloTest/headers;src/hello/headers;${prebuiltPath}/googleTest/1.7.0/include"
        }
    }

    private useConventionalSourceLocations() {
        app.library.writeSources(file("src/hello"))
        app.googleTestTests.writeSources(file("src/helloTest"))
    }

    private useFailingTestSources() {
        useConventionalSourceLocations()
        file("src/hello/cpp/sum.cpp").text = file("src/hello/cpp/sum.cpp").text.replace("return a + b;", "return 2;")
    }

    @Override
    String getOutput() {
        return normaliseLineSeparators(super.getOutput())
    }
}
