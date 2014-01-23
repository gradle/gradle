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

package org.gradle.nativebinaries.language.objectivec

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.language.cpp.AbstractLanguageIncrementalBuildIntegrationTest
import org.gradle.nativebinaries.language.cpp.fixtures.app.IncrementalHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.ObjectiveCHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Ignore

@Requires(TestPrecondition.NOT_WINDOWS)
@Ignore
class ObjectiveCLanguageIncrementalBuildIntegrationTest extends AbstractLanguageIncrementalBuildIntegrationTest{

    def setupSpec(){
        multiPlatformsAvailable = OperatingSystem.current().isMacOsX();
    }

    // TODO Rene: same configuration as in ObjectiveCLanguageIntegrationTest; Move into a fixture
    def "setup"() {
        def linkerArgs = OperatingSystem.current().isMacOsX() ? '"-framework", "Foundation"' : '"-lgnustep-base", "-lobjc"'
        buildFile << """
            binaries.all {
                if (toolChain in Gcc) {
                    objectiveCCompiler.args "-I/usr/include/GNUstep", "-fconstant-string-class=NSConstantString", "-D_NATIVE_OBJC_EXCEPTIONS"
                }

                if (toolChain in Clang) {
                    objectiveCCompiler.args "-I/usr/include/GNUstep", "-I/usr/local/include/objc", "-fconstant-string-class=NSConstantString", "-D_NATIVE_OBJC_EXCEPTIONS"
                }

                linker.args $linkerArgs
            }
        """
    }

    def "recompiles binary when #statement header file changes"() {
        println sourceFile.text
        sourceFile.text = sourceFile.text.replaceFirst('#import "hello.h"', "#$statement \"hello.h\"")
        println sourceFile.text

        given:
        run "installMainExecutable"

        when:
        headerFile << """
            int unused();
"""
        run "mainExecutable"

        then:
        executedAndNotSkipped libraryCompileTask
        executedAndNotSkipped mainCompileTask


        skipped ":linkHelloSharedLibrary", ":helloSharedLibrary"
        skipped ":linkMainExecutable", ":mainExecutable"

        where:
        statement << ["include", "import"]
    }

    @Override
    IncrementalHelloWorldApp getHelloWorldApp() {
        return new ObjectiveCHelloWorldApp()
    }
}
