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
package org.gradle.nativeplatform.test.cunit

import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.ide.visualstudio.fixtures.ProjectFile
import org.gradle.ide.visualstudio.fixtures.SolutionFile
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.CHelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.TextUtil
import spock.lang.Issue

@Requires(UnitTestPreconditions.CanInstallExecutable)
@RequiresInstalledToolChain(ToolChainRequirement.SUPPORTS_32)
class CUnitIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def prebuiltDir = buildContext.getSamplesDir().file("native-binaries/cunit/groovy/libs")
    def prebuiltPath = TextUtil.normaliseFileSeparators(prebuiltDir.path)
    def app = new CHelloWorldApp()

    def setup() {
        prebuiltDir.file("cunit/2.1-2/lib/${toolChain.unitTestPlatform}/${cunitLibName}").assumeExists()
        buildFile << """
apply plugin: 'cunit-test-suite'

model {
    repositories {
        libs(PrebuiltLibraries) {
            cunit {
                headers.srcDir "${prebuiltPath}/cunit/2.1-2/include"
                binaries.withType(StaticLibraryBinary) {
                    staticLibraryFile = file("${prebuiltPath}/cunit/2.1-2/lib/${toolChain.unitTestPlatform}/${cunitLibName}")
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
        helloTest(CUnitTestSuiteSpec) {
            testing \$.components.hello
        }
    }
    binaries {
        withType(CUnitTestSuiteBinarySpec) {
            lib library: "cunit", linkage: "static"
        }
    }
}
"""
    }

    private def getCunitLibName() {
        return OperatingSystem.current().getStaticLibraryName("cunit")
    }

    def "can build and run cunit test suite"() {
        given:
        useConventionalSourceLocations()
        useStandardConfig()

        when:
        run "check"

        then:
        executedAndNotSkipped ":compileHelloTestCUnitExeHelloC", ":compileHelloTestCUnitExeHelloTestC",
            ":linkHelloTestCUnitExe", ":helloTestCUnitExe", ":runHelloTestCUnitExe"
        file("build/test-results/helloTest/CUnitAutomated-Listing.xml").assertExists()

        def testResults = new CUnitTestResults(file("build/test-results/helloTest/CUnitAutomated-Results.xml"))
        testResults.suiteNames == ['hello test']
        testResults.suites['hello test'].passingTests == ['test_sum']
        testResults.suites['hello test'].failingTests == []
        testResults.checkTestCases(1, 1, 0)
        testResults.checkAssertions(3, 3, 0)
    }

    def "assemble does not build or run tests"() {
        given:
        useConventionalSourceLocations()
        useStandardConfig()

        when:
        run "assemble"

        then:
        notExecuted ":compileHelloTestCUnitExeHelloC", ":compileHelloTestCUnitExeHelloTestC",
            ":linkHelloTestCUnitExe", ":helloTestCUnitExe", ":runHelloTestCUnitExe"
    }

    @Issue("GRADLE-3225")
    def "can build and run cunit test suite with C and C++"() {
        given:
        useConventionalSourceLocations()
        useStandardConfig()
        buildFile << "apply plugin: 'cpp'"
        file("src/hello/cpp/foo.cpp").text = "class foobar { };"

        when:
        run "check"

        then:
        executedAndNotSkipped ":compileHelloTestCUnitExeHelloCpp", ":compileHelloTestCUnitExeHelloC", ":compileHelloTestCUnitExeHelloTestC",
            ":linkHelloTestCUnitExe", ":helloTestCUnitExe", ":runHelloTestCUnitExe"
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
        helloTest(CUnitTestSuiteSpec) {
            testing \$.components.hello
            binaries.all {
                lib library: "cunit", linkage: "static"
            }
        }
    }
}
"""

        when:
        run "check"

        then:
        executedAndNotSkipped ":compileHelloTestCUnitExeHelloC", ":compileHelloTestCUnitExeHelloTestC",
            ":linkHelloTestCUnitExe", ":helloTestCUnitExe", ":runHelloTestCUnitExe"
        file("build/test-results/helloTest/CUnitAutomated-Listing.xml").assertExists()

        def testResults = new CUnitTestResults(file("build/test-results/helloTest/CUnitAutomated-Results.xml"))
        testResults.suiteNames == ['hello test']
        testResults.suites['hello test'].passingTests == ['test_sum']
        testResults.suites['hello test'].failingTests == []
        testResults.checkTestCases(1, 1, 0)
        testResults.checkAssertions(3, 3, 0)
    }

    @ToBeFixedForConfigurationCache(because = ":model")
    def "testSuite components exposed to modelReport"() {
        given:
        buildFile << """
model {
    components {
        nativeComponentOne(NativeLibrarySpec)
        nativeComponentTwo(NativeLibrarySpec)
    }
    testSuites {
        nativeComponentOneTest(CUnitTestSuiteSpec) {
            testing \$.components.nativeComponentOne
        }
        nativeComponentTwoTest(CUnitTestSuiteSpec) {
            testing \$.components.nativeComponentTwo
        }
    }
}
"""
        when:
        run "model"

        then:
        ModelReportOutput.from(output).hasNodeStructure({
            testSuites {
                nativeComponentOneTest {
                    binaries {
                        nativeComponentOneTestCUnitExe {
                            tasks()
                            sources()
                        }
                    }
                    sources {
                        c()
                        cunitLauncher()
                    }
                }

                nativeComponentTwoTest {
                    binaries {
                        nativeComponentTwoTestCUnitExe {
                            tasks()
                            sources()
                        }
                    }
                    sources {
                        c()
                        cunitLauncher()
                    }
                }
            }
        }
        )
    }

    def "can supply cCompiler macro to cunit sources"() {
        given:
        useConventionalSourceLocations()
        useStandardConfig()

        when:
        buildFile << """
model {
    binaries {
        withType(CUnitTestSuiteBinarySpec) {
            cCompiler.define "ONE_TEST"
        }
    }
}
"""
        and:
        run "runHelloTestCUnitExe"

        then:
        def testResults = new CUnitTestResults(file("build/test-results/helloTest/CUnitAutomated-Results.xml"))
        testResults.checkAssertions(1, 1, 0)
    }

    def "can configure location of cunit test sources"() {
        given:
        useStandardConfig()
        app.library.writeSources(file("src/hello"))
        app.cunitTests.writeSources(file("src/alternateHelloTest"))

        when:
        buildFile << """
model {
    testSuites {
        helloTest {
            sources {
                c {
                    source.srcDir "src/alternateHelloTest/c"
                }
            }
        }
    }
}
"""

        then:
        succeeds "check"
        file("build/test-results/helloTest/CUnitAutomated-Listing.xml").assertExists()
    }

    def "can configure location of cunit test sources before component is declared"() {
        given:
        app.library.writeSources(file("src/hello"))
        app.cunitTests.writeSources(file("src/alternateHelloTest"))

        when:
        buildFile << """
model {
    testSuites {
        helloTest {
            sources {
                c {
                    source.srcDir "src/alternateHelloTest/c"
                }
            }
        }
    }
}
"""
        useStandardConfig()

        then:
        succeeds "check"
        file("build/test-results/helloTest/CUnitAutomated-Listing.xml").assertExists()
    }

    def "variant-dependent sources are included in test binary"() {
        given:
        app.library.headerFiles*.writeToDir(file("src/hello"))
        app.cunitTests.writeSources(file("src/helloTest"))
        app.library.sourceFiles*.writeToDir(file("src/variant"))

        when:
        buildFile << """
model {
    components {
        hello(NativeLibrarySpec) { l ->
            targetPlatform "x86"
            binaries.all {
                sources {
                    variant(CSourceSet) {
                        source.srcDir "src/variant/c"
                        lib l.sources.c
                    }
                }
            }
        }
    }
    testSuites {
        helloTest(CUnitTestSuiteSpec) {
            testing \$.components.hello
        }
    }
    binaries {
        withType(CUnitTestSuiteBinarySpec) {
            lib library: "cunit", linkage: "static"
        }
    }
}
"""

        then:
        succeeds "check"
        file("build/test-results/helloTest/CUnitAutomated-Listing.xml").assertExists()
    }

    def "can configure variant-dependent test sources"() {
        given:
        useStandardConfig()
        app.library.writeSources(file("src/hello"))
        app.cunitTests.writeSources(file("src/variantTest"))

        when:
        buildFile << """
model {
    testSuites {
        helloTest { t ->
            binaries.all {
                sources {
                    variant(CSourceSet) {
                        source.srcDir "src/variantTest/c"
                        lib t.sources.c
                        lib t.sources.cunitLauncher
                    }
                }
            }
        }
    }
}
"""

        then:
        succeeds "check"
        file("build/test-results/helloTest/CUnitAutomated-Listing.xml").assertExists()
    }

    def "test suite skipped after successful run"() {
        given:
        useStandardConfig()
        useConventionalSourceLocations()
        run "runHelloTestCUnitExe"
        executed ":helloTestCUnitExe", ":runHelloTestCUnitExe"

        when:
        run "runHelloTestCUnitExe"

        then:
        skipped ":helloTestCUnitExe", ":runHelloTestCUnitExe"
    }

    def "can build and run cunit failing test suite"() {
        when:
        useStandardConfig()
        useFailingTestSources()
        fails "runHelloTestCUnitExe"

        then:
        failure.assertHasDescription("Execution failed for task ':runHelloTestCUnitExe'.")
        failure.assertHasCause("There were failing tests. See the results at: ")

        and:
        executedAndNotSkipped ":compileHelloTestCUnitExeHelloC", ":compileHelloTestCUnitExeHelloTestC",
            ":linkHelloTestCUnitExe", ":helloTestCUnitExe", ":runHelloTestCUnitExe"
        contains "There were test failures:"

        and:
        def testResults = new CUnitTestResults(file("build/test-results/helloTest/CUnitAutomated-Results.xml"))
        testResults.suiteNames == ['hello test']
        testResults.suites['hello test'].passingTests == []
        testResults.suites['hello test'].failingTests == ['test_sum']
        testResults.checkTestCases(1, 0, 1)
        testResults.checkAssertions(3, 1, 2)
        file("build/test-results/helloTest/CUnitAutomated-Listing.xml").assertExists()
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
        succeeds "runHelloTestCUnitExe"

        then:
        contains "There were test failures:"
        contains "There were failing tests. See the results at: "

        and:
        file("build/test-results/helloTest/CUnitAutomated-Results.xml").assertExists()
        file("build/test-results/helloTest/CUnitAutomated-Listing.xml").assertExists()
    }

    def "test suite not skipped after failing run"() {
        given:
        useStandardConfig()
        useFailingTestSources()
        fails "runHelloTestCUnitExe"

        when:
        fails "runHelloTestCUnitExe"

        then:
        executedAndNotSkipped ":runHelloTestCUnitExe"
    }

    @ToBeFixedForConfigurationCache
    def "creates visual studio solution and project for cunit test suite"() {
        given:
        useStandardConfig()
        useConventionalSourceLocations()
        buildFile.text = "apply plugin: 'visual-studio'\n" + buildFile.text

        when:
        succeeds "visualStudio"

        then:
        final mainSolution = new SolutionFile(file("test.sln"))
        mainSolution.assertHasProjects("helloTestExe", "helloDll", "helloLib",)

        and:
        final projectFile = new ProjectFile(file("helloTestExe.vcxproj"))
        projectFile.sourceFiles as Set == [
            "build.gradle",
            "build/src/helloTest/cunitLauncher/c/gradle_cunit_main.c",
            "src/helloTest/c/test.c",
            "src/hello/c/hello.c",
            "src/hello/c/sum.c"
        ] as Set
        projectFile.headerFiles == [
            "build/src/helloTest/cunitLauncher/headers/gradle_cunit_register.h",
            "src/hello/headers/common.h",
            "src/hello/headers/hello.h"
        ]
        projectFile.projectConfigurations.keySet() == ['debug'] as Set
        with(projectFile.projectConfigurations['debug']) {
            includePath == "src/helloTest/headers;build/src/helloTest/cunitLauncher/headers;src/hello/headers;${prebuiltPath}/cunit/2.1-2/include"
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
        unbuildableTest(CUnitTestSuiteSpec) {
            testing \$.components.unbuildable
        }
    }
    binaries {
        unbuildableTestCUnitExe {
            buildable = false
        }
    }
}
"""

        when:
        run "check"

        then:
        notExecuted ":runUnbuildableTestCUnitExe"
        executedAndNotSkipped ":runHelloTestCUnitExe"
    }

    def "cunit run task is properly wired to binaries check tasks and lifecycle check task"() {
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
        executed ':customHelloCheck', ':checkHelloSharedLibrary', ':checkHelloStaticLibrary', ':checkHelloTestCUnitExe', ':runHelloTestCUnitExe'

        when:
        succeeds 'checkHelloTestCUnitExe'
        then:
        executed ':runHelloTestCUnitExe'

        when:
        succeeds 'checkHelloStaticLibrary'
        then:
        executed ':customHelloCheck', ':runHelloTestCUnitExe'
    }

    private useConventionalSourceLocations() {
        app.library.writeSources(file("src/hello"))
        app.cunitTests.writeSources(file("src/helloTest"))
    }

    private useFailingTestSources() {
        useConventionalSourceLocations()
        file("src/hello/c/sum.c").text = file("src/hello/c/sum.c").text.replace("return a + b;", "return 2;")
    }

    boolean contains(String content) {
        return getOutput().contains(content)
    }
}
