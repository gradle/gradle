/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.fixtures.app

class IncrementalSwiftModifyCppDepHeadersApp {
    final lib = new IncrementalCppGreeter()
    final main = new IncrementalSwiftAppWithCppDep(lib)

    IncrementalSwiftAppWithCppDep getApplication() {
        return main
    }

    String getExpectedOutput() {
        return main.expectedOutput
    }

    IncrementalCppGreeter getLibrary() {
        return lib
    }

    String getAlternateLibraryOutput() {
        return main.expectedAlternateOutput
    }

    class IncrementalCppGreeter extends IncrementalCppElement {
        final greeter = new CppGreeterFunction().asLib()
        final alternateGreeter = new CppAlternateHeaderGreeterFunction().asLib()

        final String moduleName = "cppGreeter"
        final List<IncrementalElement.Transform> incrementalChanges = [modify(greeter, alternateGreeter)]
    }

    class IncrementalSwiftAppWithCppDep extends IncrementalSwiftApp {
        final SwiftMainWithCppDep main
        final SwiftMainWithCppDep alternateMain

        IncrementalSwiftAppWithCppDep(IncrementalCppGreeter library) {
            main = new SwiftMainWithCppDep(library.greeter)
            alternateMain = new SwiftMainWithCppDep(library.alternateGreeter)
        }

        @Override
        final List<IncrementalElement.Transform> getIncrementalChanges() {
            [preserve(main)]
        }

        final String moduleName = "App"

        @Override
        final String getExpectedOutput() {
            main.expectedOutput
        }

        @Override
        final String getExpectedAlternateOutput() {
            alternateMain.expectedOutput
        }
    }
}
