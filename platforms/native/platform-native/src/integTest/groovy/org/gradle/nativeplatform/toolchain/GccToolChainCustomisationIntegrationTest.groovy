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

package org.gradle.nativeplatform.toolchain

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.app.CHelloWorldApp
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.GCC_COMPATIBLE
import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.SUPPORTS_32

@RequiresInstalledToolChain(GCC_COMPATIBLE)
class GccToolChainCustomisationIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def helloWorldApp = new CHelloWorldApp()

    def setup() {
        buildFile << """
apply plugin: 'c'

model {
    toolChains {
        ${toolChain.buildScriptConfig}
    }
    components {
        main(NativeExecutableSpec) {
            binaries.all {
                lib library: 'hello', linkage: 'static'
            }
        }
        hello(NativeLibrarySpec)
    }
}
"""

        helloWorldApp.executable.writeSources(file("src/main"))
        helloWorldApp.library.writeSources(file("src/hello"))
    }

    @RequiresInstalledToolChain(SUPPORTS_32)
    def "can configure platform specific args"() {
        when:
        buildFile << """
model {
    toolChains {
        ${toolChain.id} {
            target("arm"){
                cCompiler.withArguments { args ->
                    args << "-m32"
                    args << "-DFRENCH"
                }
                linker.withArguments { args ->
                    args << "-m32"
                }
            }
            target("sparc")
        }
    }
    platforms {
        arm {
            architecture "arm"
        }
        i386 {
            architecture "i386"
        }
        sparc {
            architecture "sparc"
        }
    }
    components {
        all {
            targetPlatform "arm"
            targetPlatform "i386"
            targetPlatform "sparc"
        }
    }
}
"""

        and:
        succeeds "mainArmExecutable", "mainI386Executable", "mainSparcExecutable"

        then:
        executable("build/exe/main/arm/main").arch.name == "x86"
        executable("build/exe/main/arm/main").exec().out == helloWorldApp.frenchOutput

        executable("build/exe/main/i386/main").arch.name == "x86"
        executable("build/exe/main/i386/main").exec().out == helloWorldApp.englishOutput

        executable("build/exe/main/sparc/main").exec().out == helloWorldApp.englishOutput
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "can configure tool executables"() {
        def binDir = testDirectory.createDir("bin")
        wrapperTool(binDir, "c-compiler", toolChain.CCompiler, "-DFRENCH")
        wrapperTool(binDir, "static-lib", toolChain.staticLibArchiver)
        wrapperTool(binDir, "linker", toolChain.linker)

        when:
        buildFile << """
model {
    toolChains {
        ${toolChain.id} {
            path file('${binDir.toURI()}')
            eachPlatform {
                cCompiler.executable = 'c-compiler'
                staticLibArchiver.executable = 'static-lib'
                linker.executable = 'linker'
            }
        }
    }
}
"""
        succeeds "mainExecutable"

        then:
        executable("build/exe/main/main").exec().out == helloWorldApp.frenchOutput
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "can configure platform specific executables"() {
        def binDir = testDirectory.createDir("bin")
        wrapperTool(binDir, "french-c-compiler", toolChain.CCompiler, "-DFRENCH")
        wrapperTool(binDir, "static-lib", toolChain.staticLibArchiver)
        wrapperTool(binDir, "linker", toolChain.linker)

        when:
        file("src/execTest/c/execTest.c") <<"""
            #include <stdio.h>

            int main () {
                #if defined(__cplusplus)
                printf("C++ compiler used");
                #else
                printf("C compiler used");
                #endif
                return 0;
            }
        """
        and:
        buildFile << """
model {
    toolChains {
        ${toolChain.id} {
            target("alwaysFrench"){
                cCompiler.executable = '${binDir.absolutePath}/french-c-compiler'
                staticLibArchiver.executable = '${binDir.absolutePath}/static-lib'
                linker.executable = '${binDir.absolutePath}/linker'
            }

            target("alwaysCPlusPlus") {
                def compilerMap = [gcc: 'g++', clang: 'clang++']
                cCompiler.executable = compilerMap[cCompiler.executable]
                cCompiler.withArguments { args ->
                    Collections.replaceAll(args, "c", "c++")
                }
            }
        }
    }

    platforms {
        alwaysFrench
        alwaysCPlusPlus
    }
    components {
        execTest(NativeExecutableSpec) {
            targetPlatform "alwaysFrench"
            targetPlatform "alwaysCPlusPlus"
        }
        main {
            targetPlatform "alwaysFrench"
            targetPlatform "alwaysCPlusPlus"
        }
        hello {
            targetPlatform "alwaysFrench"
            targetPlatform "alwaysCPlusPlus"
        }
    }
}
"""
        succeeds "assemble"
        then:
        executable("build/exe/main/alwaysFrench/main").exec().out == helloWorldApp.frenchOutput
        executable("build/exe/main/alwaysCPlusPlus/main").exec().out == helloWorldApp.englishOutput
        executable("build/exe/execTest/alwaysCPlusPlus/execTest").exec().out == "C++ compiler used"
        executable("build/exe/execTest/alwaysFrench/execTest").exec().out == "C compiler used"
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "can configure setTargets with alternate toolchain"() {
        def binDir = testDirectory.createDir("bin")
        wrapperTool(binDir, "french-c-compiler", toolChain.CCompiler, "-DFRENCH")
        wrapperTool(binDir, "static-lib", toolChain.staticLibArchiver)
        wrapperTool(binDir, "linker", toolChain.linker)

        when:
        buildFile << """
model {
    platforms {
        x86 {
            architecture 'x86'
        }
        x86_64 {
            architecture 'x64'
        }
        custom {
            architecture 'foo'
        }
    }
    toolChains {
        ${toolChain.id} {
            target('x86')
            target('x86_64')
        }
        customToolchain(${toolChain.implementationClass}) {
            setTargets('custom')
            eachPlatform {
                cCompiler.executable = '${binDir.absolutePath}/french-c-compiler'
                staticLibArchiver.executable = '${binDir.absolutePath}/static-lib'
                linker.executable = '${binDir.absolutePath}/linker'
            }
        }
    }
    components {
        all {
            targetPlatform "x86"
            targetPlatform "x86_64"
            targetPlatform "custom"
        }
    }
}
"""
        succeeds "assemble"

        then:
        executable("build/exe/main/x86/main").exec().out == helloWorldApp.englishOutput
        executable("build/exe/main/x86_64/main").exec().out == helloWorldApp.englishOutput
        executable("build/exe/main/custom/main").exec().out == helloWorldApp.frenchOutput
    }

    def wrapperTool(TestFile binDir, String wrapperName, File executable, String... additionalArgs) {
        def script = binDir.file(OperatingSystem.current().getExecutableName(wrapperName))
        if (OperatingSystem.current().windows) {
            script.text = "${executable} ${additionalArgs.join(' ')} %*"
        } else {
            script.text = "${executable} ${additionalArgs.join(' ')} \"\$@\""
            script.permissions = "rwxr--r--"
        }
        return script
    }
}
