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

package org.gradle.nativeplatform.fixtures.app

/**
 * A Swift app with library that contains a changed source file.
 */
class IncrementalSwiftModifyExpectedOutputAppWithLib {
    final lib = new IncrementalSwiftLib()
    final main = new IncrementalSwiftAppWithDep(lib)

    IncrementalSwiftAppWithDep getApplication() {
        return main
    }

    String getExpectedOutput() {
        return main.expectedOutput
    }

    IncrementalSwiftLib getLibrary() {
        return lib
    }

    String getAlternateLibraryOutput() {
        return main.expectedAlternateOutput
    }

    class IncrementalSwiftLib extends IncrementalSwiftElement implements SumElement {
        final greeter = new SwiftGreeter()
        final sum = new SwiftSum()
        final alternateGreeter = new SwiftAlternateGreeter()

        final String moduleName = "Greeter"
        final List<IncrementalElement.Transform> incrementalChanges = [modify(greeter, alternateGreeter), preserve(sum)]

        @Override
        int sum(int a, int b) {
            return sum.sum(a, b)
        }
    }

    class IncrementalSwiftAppWithDep extends IncrementalSwiftApp {
        final SwiftMain main
        final SwiftMain alternateMain

        IncrementalSwiftAppWithDep(IncrementalSwiftLib library) {
            main = new SwiftMainWithDep(library.greeter, library.sum)
            alternateMain = new SwiftMainWithDep(library.alternateGreeter, library.sum)
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
