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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.TextUtil
import spock.lang.Issue

import static org.gradle.util.internal.TextUtil.normaliseLineSeparators

@Requires(UnitTestPreconditions.CanInstallExecutable)
@RequiresInstalledToolChain(ToolChainRequirement.SUPPORTS_32)
class GoogleTestIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def prebuiltDir = buildContext.getSamplesDir().file("native-binaries/google-test/groovy/libs")
    def prebuiltPath = TextUtil.normaliseFileSeparators(prebuiltDir.path)
    def app = new CppHelloWorldApp()

    def setup() {
        prebuiltDir.file("/googleTest/1.7.0/lib/${toolChain.unitTestPlatform}/${googleTestLib}").assumeExists()
        buildFile << """
apply plugin: 'google-test-test-suite'

model {
    repositories {
        libs(PrebuiltLibraries) {
            googleTest {
                headers.srcDir "${prebuiltPath}/googleTest/1.7.0/include"
                binaries.withType(StaticLibraryBinary) {
                    staticLibraryFile = file("${prebuiltPath}/googleTest/1.7.0/lib/${toolChain.unitTestPlatform}/${googleTestLib}")
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
    testSuites {
        helloTest(GoogleTestTestSuiteSpec) {
            testing \$.components.hello
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
model {
    binaries {
        withType(GoogleTestTestSuiteBinarySpec) {
            lib library: "googleTest", linkage: "static"
            if (targetPlatform.operatingSystem.linux) {
                cppCompiler.args '-pthread'
                linker.args '-pthread'
            }
            if ((toolChain instanceof Gcc || toolChain instanceof Clang) && ${!toolChain.displayName.startsWith("gcc cygwin")}) {
                // Use C++03 with the old ABIs, as this is what the googletest binaries were built with
                // Later, Gradle's dependency management will understand ABI
                cppCompiler.args '-std=c++03', '-D_GLIBCXX_USE_CXX11_ABI=0'
                linker.args '-std=c++03'
            }
        }
    }
}
"""
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

        def testResults = new GoogleTestTestResults(file("build/test-results/helloTest/test_detail.xml"))
        testResults.suiteNames == ['HelloTest']
        testResults.suites['HelloTest'].passingTests == ['test_sum']
        testResults.suites['HelloTest'].failingTests == []
        testResults.checkTestCases(1, 1, 0)
    }

    def "assemble does not build or run tests"() {
        given:
        useConventionalSourceLocations()
        useStandardConfig()

        when:
        run "assemble"

        then:
        notExecuted ":compileHelloTestGoogleTestExeHelloCpp", ":compileHelloTestGoogleTestExeHelloTestCpp",
                ":linkHelloTestGoogleTestExe", ":helloTestGoogleTestExe", ":runHelloTestGoogleTestExe"
    }

    @Issue("GRADLE-3225")
    def "can build and run googleTest test suite with C and C++ plugins"() {
        given:
        useConventionalSourceLocations()
        useStandardConfig()
        buildFile << "apply plugin: 'c'"
        file("src/hello/c/foo.c").text = "int foobar() { return 0; }"

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
        helloTest(GoogleTestTestSuiteSpec) {
            testing \$.components.hello
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

        def testResults = new GoogleTestTestResults(file("build/test-results/helloTest/test_detail.xml"))
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
model {
    binaries {
        withType(GoogleTestTestSuiteBinarySpec) {
            cppCompiler.define "ONE_TEST"
        }
    }
}
"""
        and:
        run "runHelloTestGoogleTestExe"

        then:
        def testResults = new GoogleTestTestResults(file("build/test-results/helloTest/test_detail.xml"))
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
        hello(NativeLibrarySpec) { l ->
            targetPlatform "x86"
            binaries.all {
                sources {
                    variant(CppSourceSet) {
                        source.srcDir "src/variant/cpp"
                        lib l.sources.cpp
                    }
                }
            }
        }
    }
    testSuites {
        helloTest(GoogleTestTestSuiteSpec) {
            testing \$.components.hello
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
        helloTest { t ->
            binaries.all {
                sources {
                    variant(CppSourceSet) {
                        source.srcDir "src/variantTest/cpp"
                        lib t.sources.cpp
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
        executed ":helloTestGoogleTestExe", ":runHelloTestGoogleTestExe"

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
        def testResults = new GoogleTestTestResults(file("build/test-results/helloTest/test_detail.xml"))
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
        file("build/test-results/helloTest/test_detail.xml").assertExists()
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

    @ToBeFixedForConfigurationCache
    def "creates visual studio solution and project for googleTest test suite"() {
        given:
        useStandardConfig()
        useConventionalSourceLocations()
        buildFile.text = "apply plugin: 'visual-studio'\n" + buildFile.text

        when:
        succeeds "visualStudio"

        then:
        final mainSolution = new SolutionFile(file("test.sln"))
        mainSolution.assertHasProjects("helloTestExe", "helloLib", "helloDll")

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

    def "non-buildable binaries are not attached to check task"() {
        given:
        useConventionalSourceLocations()
        useStandardConfig()
        buildFile << """
model {
    components {
        unbuildable(NativeLibrarySpec)
    }
    testSuites {
        unbuildableTest(GoogleTestTestSuiteSpec) {
            testing \$.components.unbuildable
        }
    }
    binaries {
        unbuildableTestGoogleTestExe {
            buildable = false
        }
    }
}
"""

        when:
        run "check"

        then:
        notExecuted ":runUnbuildableTestGoogleTestExe"
        executedAndNotSkipped ":runHelloTestGoogleTestExe"
    }

    def "google test run task is properly wired to binaries check tasks and lifecycle check task"() {
        given:
        useStandardConfig()
        useConventionalSourceLocations()
        buildFile << '''
            task customHelloCheck()
            model {
                components {
                    hello {
                        binaries.all {
                            checkedBy($.tasks.customHelloCheck)
                        }
                    }
                }
            }
        '''.stripIndent()

        when:
        succeeds 'check'
        then:
        executed ':customHelloCheck', ':checkHelloSharedLibrary', ':checkHelloStaticLibrary', ':checkHelloTestGoogleTestExe', ':runHelloTestGoogleTestExe'

        when:
        succeeds 'checkHelloTestGoogleTestExe'
        then:
        executed ':runHelloTestGoogleTestExe'

        when:
        succeeds 'checkHelloStaticLibrary'
        then:
        executed ':customHelloCheck', ':runHelloTestGoogleTestExe'
    }

    @Issue("https://github.com/gradle/gradle/issues/1000")
    def "can configure legacy plugin"() {
        given:
        buildFile << """
            apply plugin: 'java'
        """

        when:
        succeeds 'tasks'

        then:
        noExceptionThrown()
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
