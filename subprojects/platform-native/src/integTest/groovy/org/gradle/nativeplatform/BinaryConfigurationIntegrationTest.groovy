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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.hamcrest.Matchers
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Unroll

class BinaryConfigurationIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def setup() {
        EnableModelDsl.enable(executer)
    }

    @LeaksFileHandles
    def "can configure the binaries of a C++ application"() {
        given:
        buildFile << """
apply plugin: "cpp"

model {
    components {
        main(NativeExecutableSpec) {
            binaries.all {
                cppCompiler.define 'ENABLE_GREETING'
            }
        }
    }
}
"""

        and:
        file("src/main/cpp/helloworld.cpp") << """
            #include <iostream>

            int main () {
              #ifdef ENABLE_GREETING
              std::cout << "Hello!";
              #endif
              return 0;
            }
        """

        when:
        run "mainExecutable"

        then:
        def executable = executable("build/binaries/mainExecutable/main")
        executable.exec().out == "Hello!"
    }

    @LeaksFileHandles("can't delete build/binaries/mainExecutable")
    def "can build debug binaries for a C++ executable"() {
        given:
        buildFile << """
apply plugin: "cpp"

model {
    components {
        main(NativeExecutableSpec) {
            binaries.all {
                if (toolChain in VisualCpp) {
                    cppCompiler.args '/Zi'
                    linker.args '/DEBUG'
                } else {
                    cppCompiler.args '-g'
                }
            }
        }
    }
}
"""

        and:
        file("src/main/cpp/helloworld.cpp") << """
            #include <iostream>

            int main () {
              std::cout << "Hello!";
              return 0;
            }
        """

        when:
        run "mainExecutable"

        then:
        def executable = executable("build/binaries/mainExecutable/main")
        executable.exec().out == "Hello!"
        executable.assertDebugFileExists()
        // TODO - need to verify that the debug info ended up in the binary
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    @LeaksFileHandles
    def "can configure the binaries of a C++ library"() {
        given:
        buildFile << """
apply plugin: "cpp"

model {
    components { comp ->
        hello(NativeLibrarySpec) {
            binaries.all {
                cppCompiler.define 'ENABLE_GREETING'
            }
        }
        main(NativeExecutableSpec) {
            binaries.all {
                lib comp.hello.static
            }
        }
    }
}
"""
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src/hello/cpp/hello.cpp") << """
            #include <iostream>

            void hello(const char* str) {
              #ifdef ENABLE_GREETING
              std::cout << str;
              #endif
            }
        """

        and:
        file("src/hello/headers/hello.h") << """
            void hello(const char* str);
        """

        and:
        file("src/main/cpp/main.cpp") << """
            #include "hello.h"

            int main () {
              hello("Hello!");
              return 0;
            }
        """

        when:
        run "installMainExecutable"

        then:
        staticLibrary("build/binaries/helloStaticLibrary/hello").assertExists()
        installation("build/install/mainExecutable").exec().out == "Hello!"
    }

    def "can customize binaries before and after linking"() {
        def helloWorldApp = new CppHelloWorldApp()
        given:
        buildFile << '''
apply plugin: 'cpp'

model {
    components {
        main(NativeExecutableSpec)
    }
    tasks { t ->
        $("components.main").binaries { binaries ->
            binaries.values().each { binary ->
                def preLinkTask = binary.name + "PreLink"
                t.create(preLinkTask) {
                    dependsOn binary.tasks.withType(CppCompile)
                    doLast {
                        println "Pre Link"
                    }
                }
                binary.tasks.link.dependsOn preLinkTask

                def postLinkTask = binary.name + "PostLink"
                t.create(postLinkTask) {
                    dependsOn binary.tasks.link
                    doLast {
                        println "Post Link"
                    }
                }
                binary.tasks.build.dependsOn postLinkTask
            }
        }
    }
}
'''

        and:
        helloWorldApp.writeSources(file("src/main"))

        when:
        succeeds "mainExecutable"

        then:
        executedTasks == [":compileMainExecutableMainCpp", ":mainExecutablePreLink", ":linkMainExecutable", ":mainExecutablePostLink", ":mainExecutable"]
    }

    @Issue("GRADLE-2973")
    @IgnoreIf({ !GradleContextualExecuter.isParallel() })
    def "releases cache lock when compilation fails with --parallel"() {
        def helloWorldApp = new CppHelloWorldApp()
        given:
        settingsFile << "include ':a', ':b'"
        buildFile << """
subprojects {
    apply plugin: 'cpp'
    model {
        components {
            main(NativeExecutableSpec)
        }
    }
}
        """

        and:
        helloWorldApp.writeSources(file("a/src/main"))
        helloWorldApp.writeSources(file("b/src/main"))

        file("b/src/main/cpp/broken.cpp") << """
    A broken C++ file
"""

        expect:
        fails "mainExecutable"
        failure.assertThatCause(Matchers.not(Matchers.containsString("Could not stop")))
    }

    def "can configure output file for binaries"() {
        given:
        def app = new CppHelloWorldApp()
        app.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))

        and:
        buildFile << """
apply plugin: 'cpp'
model {
    components {
        def modPath = { File original -> new File(original.parent + "/new_output/_" + original.name) }
        main(NativeExecutableSpec) {
            binaries.all {
                executableFile = modPath(executableFile)
            }
        }
        hello(NativeLibrarySpec) {
            binaries.withType(SharedLibraryBinarySpec) {
                sharedLibraryFile = modPath(sharedLibraryFile)
                sharedLibraryLinkFile = modPath(sharedLibraryLinkFile)
            }
            binaries.withType(StaticLibraryBinarySpec) {
                staticLibraryFile = modPath(staticLibraryFile)
            }
        }
    }
}
"""

        when:
        succeeds "mainExecutable", "helloSharedLibrary", "helloStaticLibrary"

        then:
        def modPath = {TestFile file -> new TestFile("${file.parentFile}/new_output/_${file.name}")}
        modPath(executable("build/binaries/mainExecutable/main").file).assertExists()
        modPath(sharedLibrary("build/binaries/helloSharedLibrary/hello").file).assertExists()
        modPath(staticLibrary("build/binaries/helloStaticLibrary/hello").file).assertExists()
    }

    @Unroll
    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    @LeaksFileHandles
    def "can link to #linkage library binary with custom output file"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))

        and:
        buildFile << """
apply plugin: 'cpp'
model {
    components {
        def modPath = { File original -> new File(original.parent + "/new_output/_" + original.name) }
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: "hello", linkage: "${linkage}"
            }
        }
        hello(NativeLibrarySpec) {
            binaries.withType(SharedLibraryBinarySpec) {
                sharedLibraryFile = modPath(sharedLibraryFile)
                sharedLibraryLinkFile = modPath(sharedLibraryLinkFile)
            }
            binaries.withType(StaticLibraryBinarySpec) {
                staticLibraryFile = modPath(staticLibraryFile)
            }
        }
    }
}
"""

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == app.englishOutput

        where:
        linkage << ["static", "shared"]
    }

    @Issue("GRADLE-3332")
    @NotYetImplemented
    def "can create helper task to install buildable executables"() {
        def helloWorldApp = new CppHelloWorldApp()
        given:
        buildFile << '''
apply plugin: 'cpp'

model {
    components {
        main(NativeExecutableSpec)
    }
}
task installDeveloperImage {
    description = "Install all debug executables"
    binaries.withType(NativeExecutableBinary) {
        if (it.buildable && buildType == buildTypes.debug) {
            dependsOn it.tasks.install
        }
    }
}
'''
        and:
        helloWorldApp.writeSources(file("src/main"))

        expect:
        succeeds "tasks"
    }
}
