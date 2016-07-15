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

package org.gradle.nativeplatform.fixtures

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.binaryinfo.BinaryInfo
import org.gradle.nativeplatform.fixtures.binaryinfo.DumpbinBinaryInfo
import org.gradle.nativeplatform.fixtures.binaryinfo.FileArchOnlyBinaryInfo
import org.gradle.nativeplatform.fixtures.binaryinfo.OtoolBinaryInfo
import org.gradle.nativeplatform.fixtures.binaryinfo.ReadelfBinaryInfo
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestFile.Snapshot

class NativeBinaryFixture {
    final TestFile file
    protected final AvailableToolChains.InstalledToolChain toolChain

    NativeBinaryFixture(TestFile file, AvailableToolChains.InstalledToolChain toolChain) {
        this.file = file
        this.toolChain = toolChain
    }

    URI toURI() {
        file.toURI()
    }

    Snapshot snapshot() {
        file.snapshot()
    }

    void assertHasChangedSince(Snapshot snapshot) {
        file.assertContentsHaveChangedSince(snapshot)
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
            getDebugFile().assertIsFile()
        }
    }

    // Does nothing when tool chain does not generate a separate debug file
    void assertDebugFileDoesNotExist() {
        if (toolChain.visualCpp) {
            getDebugFile().assertDoesNotExist()
        }
    }

    private TestFile getDebugFile() {
        return file.withExtension("pdb")
    }

    boolean assertExistsAndDelete() {
        assertExists()
        file.delete()
    }

    ArchitectureInternal getArch() {
        file.assertExists()
        BinaryInfo info
        if (OperatingSystem.current().isWindows() && !DumpbinBinaryInfo.findVisualStudio()) {
            info = new FileArchOnlyBinaryInfo(file)
        } else {
            info = getBinaryInfo()
        }
        return info.getArch()
    }

    BinaryInfo getBinaryInfo() {
        file.assertExists()
        if (OperatingSystem.current().isMacOsX()) {
            return new OtoolBinaryInfo(file);
        }
        if (OperatingSystem.current().isWindows()) {
            return new DumpbinBinaryInfo(file);
        }
        return new ReadelfBinaryInfo(file);
    }
}
