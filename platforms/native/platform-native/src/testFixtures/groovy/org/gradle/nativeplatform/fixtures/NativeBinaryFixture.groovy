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
import org.gradle.nativeplatform.fixtures.binaryinfo.DumpbinGccProducedBinaryInfo
import org.gradle.nativeplatform.fixtures.binaryinfo.FileArchOnlyBinaryInfo
import org.gradle.nativeplatform.fixtures.binaryinfo.OtoolBinaryInfo
import org.gradle.nativeplatform.fixtures.binaryinfo.ReadelfBinaryInfo
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.toolchain.internal.SymbolExtractorOsConfig
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestFile.Snapshot
import org.gradle.util.internal.VersionNumber

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
            getSymbolFile().assertIsFile()
        }
    }

    // Does nothing when tool chain does not generate a separate debug file
    void assertDebugFileDoesNotExist() {
        if (toolChain.visualCpp) {
            getSymbolFile().assertDoesNotExist()
        }
    }

    private TestFile getSymbolFile() {
        if (toolChain?.visualCpp) {
            return file.withExtension(".pdb")
        } else {
            return strippedRuntimeFile.withExtension(SymbolExtractorOsConfig.current().extension)
        }
    }

    boolean assertExistsAndDelete() {
        assertExists()
        file.delete()
    }

    TestFile getStrippedRuntimeFile() {
        if (toolChain?.visualCpp) {
            return file
        } else {
            return file.parentFile.file("stripped/${file.name}")
        }
    }

    private NativeBinaryFixture getStrippedBinaryFixture() {
        return new NativeBinaryFixture(strippedRuntimeFile, toolChain)
    }

    private NativeBinaryFixture getSymbolFileFixture() {
        return new NativeBinaryFixture(symbolFile, toolChain)
    }

    void assertHasStrippedDebugSymbolsFor(List<String> sourceFileNames) {
        if (toolChain?.visualCpp) {
            // There is not a built-in tool for querying pdb files, so we just check that the debug file exists
            assertDebugFileExists()
        } else {
            assertHasDebugSymbolsFor(sourceFileNames)
            strippedBinaryFixture.assertDoesNotHaveDebugSymbolsFor(sourceFileNames)
            symbolFileFixture.assertHasDebugSymbolsFor(sourceFileNames)
        }
    }

    void assertHasDebugSymbolsFor(List<String> sourceFileNames) {
        assert !sourceFileNames.isEmpty()

        if (toolChain?.visualCpp) {
            // There is not a built-in tool for querying pdb files, so we just check that the debug file exists
            assertDebugFileExists()
        } else if (toolChain.meets(ToolChainRequirement.GCC) && OperatingSystem.current().windows) {
            // Currently cannot probe the actual symbols yet, just verify that there are some
            binaryInfo.assertHasDebugSymbols()
        } else {
            def symbols = binaryInfo.listDebugSymbols()
            def symbolNames = symbols.collect { it.name }
            def isOlderSwiftc = toolChain.meets(ToolChainRequirement.SWIFTC) && toolChain.version < VersionNumber.version(5, 10)
            sourceFileNames.each { sourceFileName ->
                if (sourceFileName in symbolNames) {
                    return
                }
                if (isOlderSwiftc) {
                    // Older versions used the object file instead of source file in some cases
                    def objFileName = sourceFileName.replace(".swift", ".o")
                    if (symbolNames.any { it.endsWith(objFileName) }) {
                        return
                    }
                }
                throw new AssertionError("Could not find source file '$sourceFileName' in symbols $symbolNames")
            }
        }
    }

    void assertDoesNotHaveDebugSymbolsFor(List<String> sourceFileNames) {
        if (toolChain.meets(ToolChainRequirement.GCC) && OperatingSystem.current().windows) {
            // Currently cannot probe the actual symbols yet, just verify that there are none
            binaryInfo.assertDoesNotHaveDebugSymbols()
        } else if (toolChain?.visualCpp) {
            def symbols = binaryInfo.listDebugSymbols()
            def symbolNames = symbols.collect { it.name }
            sourceFileNames.each { sourceFileName ->
                assert !(sourceFileName in symbolNames)
            }
        }
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
            return new OtoolBinaryInfo(file, toolChain.runtimeEnv)
        }
        if (OperatingSystem.current().isWindows()) {
            if (toolChain.meets(ToolChainRequirement.GCC)) {
                return new DumpbinGccProducedBinaryInfo(file, toolChain.runtimeEnv)
            }
            return new DumpbinBinaryInfo(file)
        }
        return new ReadelfBinaryInfo(file, toolChain.runtimeEnv)
    }
}
