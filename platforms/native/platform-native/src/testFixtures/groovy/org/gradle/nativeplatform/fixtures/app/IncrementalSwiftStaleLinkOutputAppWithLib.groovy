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
 * A Swift app depending on a library that remove all sources.
 */
class IncrementalSwiftStaleLinkOutputAppWithLib {
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

    class IncrementalSwiftLib extends IncrementalSwiftElement {
        final greeter = new SwiftGreeter()
        final sum = new SwiftSum()

        final String moduleName = "Greeter"
        final List<IncrementalElement.Transform> incrementalChanges = [preserve(greeter), preserve(sum)]
    }

    class IncrementalSwiftAppWithDep extends IncrementalSwiftElement implements AppElement {
        final SwiftMain main

        IncrementalSwiftAppWithDep(IncrementalSwiftLib library) {
            main = new SwiftMainWithDep(library.greeter, library.sum)
        }

        @Override
        final List<IncrementalElement.Transform> getIncrementalChanges() {
            [delete(main)]
        }

        final String moduleName = "App"

        @Override
        final String getExpectedOutput() {
            main.expectedOutput
        }
    }
}
