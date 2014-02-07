/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.app.DuplicateAssemblerBaseNamesTestApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.DuplicateCBaseNamesTestApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.DuplicateCppBaseNamesTestApp

// TODO add coverage for windows-resources, objective-c/c++ & mixed sources
class DuplicateBaseNamesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def "can have sourcefiles with same base name but different directories"() {
        setup:
        testApp.writeSources(file("src/main"))
        buildFile.text = ""
        testApp.plugins.each{ plugin ->
            buildFile << "apply plugin: '$plugin'\n"
        }

        buildFile << """
            model {
                platforms {
                    x86 {
                        architecture "i386"
                    }
                }
            }
            executables {
                main {}
            }

            """
        expect:
        executer.withArgument("-i")
        succeeds "mainExecutable"
        executable("build/binaries/mainExecutable/main").exec().out == "foo1foo2"
        where:
        testApp << [
                new DuplicateCBaseNamesTestApp(),
                new DuplicateCppBaseNamesTestApp(),
                new DuplicateAssemblerBaseNamesTestApp(toolChain) ]
    }
}


