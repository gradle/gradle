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

package org.gradle.nativecode.language.cpp.fixtures

import org.gradle.test.fixtures.file.TestFile

class NativeBinaryFixture {
    protected final TestFile file
    protected final AvailableToolChains.ToolChainCandidate toolChain

    NativeBinaryFixture(TestFile file, AvailableToolChains.ToolChainCandidate toolChain) {
        this.file = file
        this.toolChain = toolChain
    }

    URI toURI() {
        file.toURI()
    }

    TestFile.Snapshot snapshot() {
        file.snapshot()
    }

    void assertHasChangedSince(TestFile.Snapshot snapshot) {
        file.assertHasChangedSince(snapshot)
    }

    void assertExists() {
        file.assertIsFile()
    }

    void assertDoesNotExist() {
        file.assertDoesNotExist()
    }

    // Does nothing when tool chain does not generate a separate debug file
    void assertDebugFileExists() {
        if (toolChain.visualCpp) {
            debugFile.assertIsFile()
        }
    }

    // Does nothing when tool chain does not generate a separate debug file
    void assertDebugFileDoesNotExist() {
        if (toolChain.visualCpp) {
            debugFile.assertDoesNotExist()
        }
    }

    private TestFile getDebugFile() {
        return file.withExtension("pdb")
    }
}
