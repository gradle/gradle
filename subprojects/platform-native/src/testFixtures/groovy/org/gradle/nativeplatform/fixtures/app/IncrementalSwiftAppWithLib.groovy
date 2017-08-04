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
        assert lib.sourceFiles.size() > 1
        assert alternateLib.sourceFiles.size() == lib.sourceFiles.size()
        assert alternateLib.sourceFiles.first().content != lib.sourceFiles.first().content
        for (int i = 1; i < lib.sourceFiles.size(); i++) {
            assert alternateLib.sourceFiles[i].content == lib.sourceFiles[i].content
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
