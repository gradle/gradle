/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.TextUtil

@Requires(UnitTestPreconditions.CanInstallExecutable)
@RequiresInstalledToolChain(ToolChainRequirement.SUPPORTS_32)
class GoogleTestDependentComponentsIntegrationSpec extends AbstractInstalledToolChainIntegrationSpec {

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
        """.stripIndent()
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
        """.stripIndent()
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
        """.stripIndent()
    }

    private def getGoogleTestLib() {
        return OperatingSystem.current().getStaticLibraryName("gtest")
    }

    private useConventionalSourceLocations() {
        app.library.writeSources(file("src/hello"))
        app.googleTestTests.writeSources(file("src/helloTest"))
    }

    @ToBeFixedForConfigurationCache
    def "buildDependentsHello assemble and check all hello binaries"() {
        given:
        useConventionalSourceLocations()
        useStandardConfig()

        when:
        succeeds 'buildDependentsHello'

        then:
        executed ':helloSharedLibrary', ':helloStaticLibrary', ':helloTestGoogleTestExe', ':runHelloTestGoogleTestExe'
    }

    @ToBeFixedForConfigurationCache
    def "buildDependentsHelloSharedLibrary assemble and check hello shared library"() {
        given:
        useConventionalSourceLocations()
        useStandardConfig()

        when:
        succeeds 'buildDependentsHelloSharedLibrary'

        then:
        executed ':helloSharedLibrary'
        notExecuted ':helloTestGoogleTestExe', ':runHelloTestGoogleTestExe'
    }

    @ToBeFixedForConfigurationCache
    def "buildDependentsHelloStaticLibrary assemble and check hello static library"() {
        given:
        useConventionalSourceLocations()
        useStandardConfig()

        when:
        succeeds 'buildDependentsHelloStaticLibrary'

        then:
        executed ':helloStaticLibrary', ':helloTestGoogleTestExe', ':runHelloTestGoogleTestExe'
    }

    @ToBeFixedForConfigurationCache
    def "buildDependentsHelloTestCUnitExe assemble and run test suite"() {
        given:
        useConventionalSourceLocations()
        useStandardConfig()

        when:
        succeeds 'buildDependentsHelloTestGoogleTestExe'

        then:
        executed ':helloTestGoogleTestExe', ':runHelloTestGoogleTestExe'
    }
}
