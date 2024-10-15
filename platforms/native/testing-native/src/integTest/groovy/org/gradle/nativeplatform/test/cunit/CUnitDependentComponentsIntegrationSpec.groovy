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

package org.gradle.nativeplatform.test.cunit

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.CHelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.TextUtil

@Requires(UnitTestPreconditions.CanInstallExecutable)
@RequiresInstalledToolChain(ToolChainRequirement.SUPPORTS_32)
class CUnitDependentComponentsIntegrationSpec extends AbstractInstalledToolChainIntegrationSpec {

    def prebuiltDir = buildContext.getSamplesDir().file("native-binaries/cunit/groovy/libs")
    def prebuiltPath = TextUtil.normaliseFileSeparators(prebuiltDir.path)
    def app = new CHelloWorldApp()

    def setup() {
        prebuiltDir.file("cunit/2.1-2/lib/${toolChain.unitTestPlatform}/${cunitLibName}").assumeExists()
        settingsFile << "rootProject.name = 'test'"
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
        """.stripIndent()
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
        """.stripIndent()
    }

    private useConventionalSourceLocations() {
        app.library.writeSources(file("src/hello"))
        app.cunitTests.writeSources(file("src/helloTest"))
    }

    private def getCunitLibName() {
        return OperatingSystem.current().getStaticLibraryName("cunit")
    }

    def "buildDependentsHello assemble and check all hello binaries"() {
        given:
        useConventionalSourceLocations()
        useStandardConfig()

        when:
        succeeds 'buildDependentsHello'

        then:
        executed ':helloSharedLibrary', ':helloStaticLibrary', ':helloTestCUnitExe', ':runHelloTestCUnitExe'
    }

    def "buildDependentsHelloSharedLibrary assemble and check hello shared library"() {
        given:
        useConventionalSourceLocations()
        useStandardConfig()

        when:
        succeeds 'buildDependentsHelloSharedLibrary'

        then:
        executed ':helloSharedLibrary'
        notExecuted ':helloTestCUnitExe', ':runHelloTestCUnitExe'
    }

    def "buildDependentsHelloStaticLibrary assemble and check hello static library"() {
        given:
        useConventionalSourceLocations()
        useStandardConfig()

        when:
        succeeds 'buildDependentsHelloStaticLibrary'

        then:
        executed ':helloStaticLibrary', ':helloTestCUnitExe', ':runHelloTestCUnitExe'
    }

    def "buildDependentsHelloTestCUnitExe assemble and run test suite"() {
        given:
        useConventionalSourceLocations()
        useStandardConfig()

        when:
        succeeds 'buildDependentsHelloTestCUnitExe'

        then:
        executed ':helloTestCUnitExe', ':runHelloTestCUnitExe'
    }
}
