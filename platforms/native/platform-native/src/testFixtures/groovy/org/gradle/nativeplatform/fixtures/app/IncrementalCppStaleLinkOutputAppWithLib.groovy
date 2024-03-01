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

/**
 * A Cpp app depending on a library that remove all sources.
 */
class IncrementalCppStaleLinkOutputAppWithLib {
    final lib = new IncrementalCppLib()
    final main = new IncrementalCppAppWithDep(lib)

    IncrementalCppAppWithDep getExecutable() {
        return main
    }

    String getExpectedOutput() {
        return main.expectedOutput
    }

    IncrementalCppLib getLibrary() {
        return lib
    }

    class IncrementalCppLib extends IncrementalCppElement {
        final greeter = new CppGreeter()
        final sum = new CppSum()

        final List<IncrementalElement.Transform> incrementalChanges = [preserve(greeter.asLib()), preserve(sum.asLib())]
    }

    class IncrementalCppAppWithDep extends IncrementalCppElement implements AppElement {
        final CppMain main

        IncrementalCppAppWithDep(IncrementalCppLib library) {
            main = new CppMain(library.greeter, library.sum)
        }

        @Override
        final List<IncrementalElement.Transform> getIncrementalChanges() {
            [delete(main)]
        }

        @Override
        final String getExpectedOutput() {
            main.expectedOutput
        }
    }
}
