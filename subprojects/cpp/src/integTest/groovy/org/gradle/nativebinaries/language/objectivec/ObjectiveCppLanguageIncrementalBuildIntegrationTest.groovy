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

package org.gradle.nativebinaries.language.objectivec

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.language.cpp.AbstractLanguageIncrementalBuildIntegrationTest
import org.gradle.nativebinaries.language.cpp.fixtures.app.IncrementalHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.ObjectiveCppHelloWorldApp
import org.junit.Ignore

@Ignore
class ObjectiveCppLanguageIncrementalBuildIntegrationTest  extends AbstractLanguageIncrementalBuildIntegrationTest{

    // TODO Rene: same configuration as in ObjectiveCLanguageIntegrationTest; Move into a fixture
    def "setup"() {
        def linkerArgs = OperatingSystem.current().isMacOsX() ? '"-framework", "Foundation"' : '"-lgnustep-base", "-lobjc"'
        buildFile << """
            binaries.all {
                if (toolChain in Gcc) {
                    objectiveCppCompiler.args "-I/usr/include/GNUstep", "-fconstant-string-class=NSConstantString", "-D_NATIVE_OBJC_EXCEPTIONS"
                }

                if (toolChain in Clang) {
                    objectiveCppCompiler.args "-I/usr/include/GNUstep", "-I/usr/local/include/objc", "-fconstant-string-class=NSConstantString", "-D_NATIVE_OBJC_EXCEPTIONS"
                }

                linker.args $linkerArgs
            }
        """
    }

    @Override
    IncrementalHelloWorldApp getHelloWorldApp() {
        return new ObjectiveCppHelloWorldApp()
    }
}
