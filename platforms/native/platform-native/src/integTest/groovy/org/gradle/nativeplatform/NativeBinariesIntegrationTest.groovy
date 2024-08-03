/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.nativeplatform

import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.NativePlatformsTestFixture
import org.gradle.nativeplatform.fixtures.app.CHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.CppCallingCHelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.gradle.util.Matchers.containsText

class NativeBinariesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def helloWorldApp = new CppCallingCHelloWorldApp()

    def "setup"() {
        settingsFile << "rootProject.name = 'test'"
    }

    def "skips building executable binary with no source"() {
        given:
        buildFile << """
apply plugin: "cpp"
model {
    components {
        main(NativeExecutableSpec)
    }
}
"""

        when:
        succeeds "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/main").assertDoesNotExist()
    }

    def "assemble task constructs all buildable binaries"() {
        given:
        new CHelloWorldApp().writeSources(file("src/main"))

        and:
        buildFile << """
import org.gradle.nativeplatform.platform.internal.NativePlatforms

apply plugin: 'c'

model {
    platforms {
        unknown {
            architecture "unknown"
        }
    }
}
model {
    components {
        main(NativeExecutableSpec) {
            targetPlatform "unknown"
            targetPlatform "${NativePlatformsTestFixture.defaultPlatformName}"
        }
    }
}
"""
        when:
        succeeds "assemble"

        then:
        executedAndNotSkipped ":main${NativePlatformsTestFixture.defaultPlatformName.capitalize()}Executable"
        notExecuted ":mainUnknownExecutable"

        and:
        executable("build/exe/main/${NativePlatformsTestFixture.defaultPlatformName}/main").assertExists()
        executable("build/exe/main/unknown/main").assertDoesNotExist()
    }

    @ToBeFixedForConfigurationCache
    def "assemble task produces sensible error when there are no buildable binaries"() {
        buildFile << """
apply plugin: 'c'

model {
    platforms {
        unknown {
            architecture "unknown"
        }
    }
}
model {
    components {
        main(NativeExecutableSpec) {
            targetPlatform "unknown"
        }
        hello(NativeLibrarySpec) {
            targetPlatform "unknown"
        }
        another(NativeLibrarySpec) {
            binaries.all { buildable = false }
        }
    }
}
"""
        when:
        fails "assemble"

        then:
        failureDescriptionContains("Execution failed for task ':assemble'.")
        failure.assertHasCause("""No buildable binaries found:
  - shared library 'another:sharedLibrary': Disabled by user
  - static library 'another:staticLibrary': Disabled by user
  - shared library 'hello:sharedLibrary':
      - No tool chain is available to build for platform 'unknown':
          - ${toolChain.instanceDisplayName}:
              - Don't know how to build for platform 'unknown'.
  - static library 'hello:staticLibrary':
      - No tool chain is available to build for platform 'unknown':
          - ${toolChain.instanceDisplayName}:
              - Don't know how to build for platform 'unknown'.
  - executable 'main:executable':
      - No tool chain is available to build for platform 'unknown':
          - ${toolChain.instanceDisplayName}:
              - Don't know how to build for platform 'unknown'.""")
    }

    def "assemble executable from component with multiple language source sets"() {
        given:
        useMixedSources()

        when:
        buildFile << """
apply plugin: "c"
apply plugin: "cpp"

model {
    components {
        main(NativeExecutableSpec) {
            sources {
                c {
                    source.srcDir "src/test/c"
                    exportedHeaders.srcDir "src/test/headers"
                }
                cpp {
                    source.srcDir "src/test/cpp"
                    exportedHeaders.srcDir "src/test/headers"
                }
            }
        }
    }
}
"""

        then:
        succeeds "mainExecutable"

        and:
        executable("build/exe/main/main").exec().out == helloWorldApp.englishOutput
    }

    def "assemble executable binary directly from language source sets"() {
        given:
        useMixedSources()

        when:
        buildFile << """
apply plugin: "c"
apply plugin: "cpp"

model {
    components {
        main(NativeExecutableSpec)
        all {
            binaries {
                all {
                    sources {
                        testCpp(CppSourceSet) {
                            source.srcDir "src/test/cpp"
                            exportedHeaders.srcDir "src/test/headers"
                        }
                        testC(CSourceSet) {
                            source.srcDir "src/test/c"
                            exportedHeaders.srcDir "src/test/headers"
                        }
                    }
                }
            }
        }
    }
}
"""

        then:
        succeeds "mainExecutable"

        and:
        executable("build/exe/main/main").exec().out == helloWorldApp.englishOutput
    }

    private def useMixedSources() {
        helloWorldApp.writeSources(file("src/test"))
    }

    def "build fails when link executable fails"() {
        given:
        buildFile << """
apply plugin: "cpp"
model {
    components {
        main(NativeExecutableSpec)
    }
}
"""

        and:
        file("src", "main", "cpp", "helloworld.cpp") << """
            int thing() { return 0; }
        """

        expect:
        fails "mainExecutable"
        failure.assertHasDescription("Execution failed for task ':linkMainExecutable'.");
        failure.assertHasCause("A build operation failed.")
        def exeName = executable("build/binaries/mainExecutable/main").file.name
        failure.assertThatCause(containsText("Linker failed while linking ${exeName}"))
    }

    def "build fails when link library fails"() {
        given:
        buildFile << """
apply plugin: "cpp"
model {
    components {
        main(NativeLibrarySpec)
    }
}
"""

        and:
        file("src/main/cpp/hello1.cpp") << """
            void hello() {
            }
"""

        and:
        file("src/main/cpp/hello2.cpp") << """
            void hello() {
            }
"""

        when:
        fails "mainSharedLibrary"

        then:
        failure.assertHasDescription("Execution failed for task ':linkMainSharedLibrary'.");
        failure.assertHasCause("A build operation failed.")
        def libName = sharedLibrary("build/binaries/mainSharedLibrary/main").file.name
        failure.assertThatCause(containsText("Linker failed while linking ${libName}"))
    }

    def "build fails when create static library fails"() {
        given:
        buildFile << """
apply plugin: "cpp"
model {
    components {
        main(NativeLibrarySpec)
    }
    binaries {
        withType(StaticLibraryBinarySpec) {
            staticLibArchiver.args "not_a_file"
        }
    }
}

        """

        and:
        file("src/main/cpp/hello.cpp") << """
            void hello() {
            }
"""

        when:
        fails "mainStaticLibrary"

        then:
        failure.assertHasDescription("Execution failed for task ':createMainStaticLibrary'.");
        failure.assertHasCause("A build operation failed.")
        def libName = staticLibrary("build/binaries/mainSharedLibrary/main").file.name
        failure.assertThatCause(containsText("Static library archiver failed while archiving ${libName}"))
    }

    @Requires(UnitTestPreconditions.CanInstallExecutable)
    def "installed executable receives command-line parameters"() {
        buildFile << """
apply plugin: 'c'

model {
    components {
        echo(NativeExecutableSpec)
    }
}
"""
        file("src/echo/c/main.c") << """
// Simple hello world app
#include <stdio.h>

// Print the command line args
int main (int argc, char *argv[]) {
    int i;

    for (i = 1; i < argc; i++) {
        printf("[%s] ", argv[i]);
    }
    printf("\\n");
    return 0;
}
"""

        when:
        succeeds "installEchoExecutable"

        then:
        def installation = installation("build/install/echo")
        installation.exec().out == "\n"
        installation.exec("foo", "bar").out == "[foo] [bar] \n"
    }

    @ToBeFixedForConfigurationCache(because = ":model")
    def "model report should display configured components"() {
        given:
        buildFile << """
            apply plugin: "c"
            apply plugin: "cpp"
            model {
                components {
                    exe(NativeExecutableSpec) {
                        binaries {
                            all {
                                sources {
                                    other(CSourceSet)
                                }
                            }
                        }
                    }
                    lib(NativeLibrarySpec)
                }
            }
"""
        when:
        succeeds "model"

        then:
        ModelReportOutput.from(output).hasNodeStructure {
            components {
                exe {
                    binaries {
                        executable(type: "org.gradle.nativeplatform.NativeExecutableBinarySpec") {
                            sources {
                                other(type: "org.gradle.language.c.CSourceSet")
                            }
                            tasks()
                        }
                    }
                    sources {
                        c(type: "org.gradle.language.c.CSourceSet")
                        cpp(type: "org.gradle.language.cpp.CppSourceSet")
                    }
                }
                lib {
                    binaries {
                        sharedLibrary(type: "org.gradle.nativeplatform.SharedLibraryBinarySpec") {
                            sources()
                            tasks()
                        }
                        staticLibrary(type: "org.gradle.nativeplatform.StaticLibraryBinarySpec") {
                            sources()
                            tasks()
                        }
                    }
                    sources {
                        c(type: "org.gradle.language.c.CSourceSet")
                        cpp(type: "org.gradle.language.cpp.CppSourceSet")
                    }
                }
            }
        }
    }
}
