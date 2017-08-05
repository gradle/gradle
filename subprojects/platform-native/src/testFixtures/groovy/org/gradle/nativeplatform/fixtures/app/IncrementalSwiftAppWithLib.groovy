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
class IncrementalSwiftAppWithLib {
    def lib = new SwiftLib()
    def alternateLib = new SwiftAlternateLib()
    def main = new SwiftAppWithDep(lib, lib)
    def mainWithAlternateLib = new SwiftAppWithDep(alternateLib, alternateLib)

    IncrementalSwiftAppWithLib() {
        // Verify some assumptions that the tests make
        assert lib.files.size() > 1
        assert alternateLib.files.size() == lib.files.size()
        for (int i = 0; i < lib.files.size(); i++) {
            def newSource = alternateLib.files[i]
            def oldSource = lib.files[i]
            assert newSource.path == oldSource.path
            if (i == 0) {
                assert newSource.content != oldSource.content
            } else {
                assert newSource.content == oldSource.content
            }
        }
    }

    SwiftAppWithDep getExecutable() {
        return main
    }

    String getExpectedOutput() {
        return main.expectedOutput
    }

    SwiftLib getLibrary() {
        return lib
    }

    SwiftAlternateLib getAlternateLibrary() {
        return alternateLib
    }

    String getAlternateLibraryOutput() {
        return mainWithAlternateLib.expectedOutput
    }
}
