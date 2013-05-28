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
package org.gradle.nativecode.language.cpp

import org.gradle.nativecode.language.cpp.fixtures.AbstractBinariesIntegrationSpec

import static org.gradle.util.TextUtil.escapeString

class CppPluginIncrementalBuildIntegrationTest extends AbstractBinariesIntegrationSpec {

    static final HELLO_WORLD = "Hello, World!"
    static final HELLO_WORLD_FRENCH = "Bonjour, Monde!"

    def sourceFile

    def "setup"() {
        buildFile << """
            apply plugin: "cpp-exe"
        """
        settingsFile << "rootProject.name = 'test'"
        sourceFile = file("src", "main", "cpp", "helloworld.cpp") << """
            // Simple hello world app
            #include <iostream>

            int main () {
                std::cout << "${escapeString(HELLO_WORLD)}";
                #ifdef FRENCH
                std::cout << " ${escapeString(HELLO_WORLD_FRENCH)}";
                #endif
                return 0;
            }
        """
        run "mainExecutable"
    }

    def "does not re-execute build with no change"() {
        when:
        run "mainExecutable"

        then:
        ":mainExecutable" in skippedTasks
        ":compileMainExecutable" in skippedTasks
    }

    def "rebuilds binary with source file change"() {
        when:
        sourceFile.text = """
            #include <iostream>

            int main () {
              std::cout << "changed";
              return 0;
            }
"""
        run "mainExecutable"

        then:
        executedAndNotSkipped ":compileMainExecutable", ":mainExecutable"

        and:
        def executable = executable("build/binaries/test")
        executable.isFile()
        executable.exec().out == "changed"
    }

    def "rebuilds binary with compiler option change"() {
        when:
        def executable = executable("build/binaries/test")
        def snapshot = executable.snapshot()

        and:
        buildFile << """
            executables {
                main {
                    compilerArgs '-DFRENCH'
                }
            }
"""

        run "mainExecutable"

        then:
        executedAndNotSkipped ":compileMainExecutable", ":mainExecutable"

        and:
        executable.isFile()
        executable.assertHasChangedSince(snapshot)
        executable.exec().out == "$HELLO_WORLD $HELLO_WORLD_FRENCH"
    }

    def "relinks binary but does not recompile when linker option changed"() {
        when:
        def executable = executable("build/binaries/test")
        def snapshot = executable.snapshot()

        and:
        def linkerArgs = toolChain.isVisualCpp() ? "'/DEBUG'" : "'-S'"
        linkerArgs = escapeString(linkerArgs)
        buildFile << """
            executables {
                main {
                    linkerArgs ${escapeString(linkerArgs)}
                }
            }
"""

        run "mainExecutable"

        then:
        ":compileMainExecutable" in skippedTasks
        executedAndNotSkipped ":mainExecutable"

        and:
        executable.isFile()
        executable.assertHasChangedSince(snapshot)
        executable.exec().out == HELLO_WORLD
    }

    // TODO:DAZ test incremental build with static library too
    def "recompiles source but does not relink binary with source comment change"() {
        // TODO:DAZ Better way to do this
        if (toolChain.isVisualCpp()) {
            return // Visual C++ compiler embeds a timestamp in every object file, so relinking is always required after recompiling
        }
        when:
        sourceFile.text = sourceFile.text.replaceFirst("// Simple hello world app", "// Comment is changed")
        run "mainExecutable"

        then:
        executedAndNotSkipped ":compileMainExecutable"
        ":mainExecutable" in skippedTasks
    }

}
