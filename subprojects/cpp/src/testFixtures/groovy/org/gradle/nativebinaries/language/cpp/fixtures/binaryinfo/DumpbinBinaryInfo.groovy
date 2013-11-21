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

package org.gradle.nativebinaries.language.cpp.fixtures.binaryinfo
import org.gradle.nativebinaries.internal.ArchitectureInternal
import org.gradle.nativebinaries.internal.ArchitectureNotationParser;
import org.gradle.nativebinaries.internal.DefaultArchitecture
import org.gradle.nativebinaries.internal.DefaultPlatform;
import org.gradle.nativebinaries.internal.OperatingSystemNotationParser;
import org.gradle.nativebinaries.language.cpp.fixtures.AvailableToolChains.InstalledToolChain
import org.gradle.nativebinaries.toolchain.internal.msvcpp.DefaultVisualStudioLocator
import org.gradle.nativebinaries.toolchain.internal.msvcpp.VisualStudioInstall

class DumpbinBinaryInfo implements BinaryInfo {
    final File binaryFile
    final File vcBin
    final File commonBin

    DumpbinBinaryInfo(File binaryFile, InstalledToolChain tc) {
        this.binaryFile = binaryFile

        VisualStudioInstall vsInstall = findVisualStudio()
        DefaultPlatform targetPlatform = new DefaultPlatform("default", ArchitectureNotationParser.parser(), OperatingSystemNotationParser.parser());
        vcBin = vsInstall.getVisualCppBin(targetPlatform)
        commonBin = vsInstall.getCommonIdeBin()
    }

    static VisualStudioInstall findVisualStudio() {
        def searchResult = new DefaultVisualStudioLocator().locateDefaultVisualStudio();
        new VisualStudioInstall(searchResult.result, searchResult.version);
    }

    private findExe(String exe) {
        final candidate = new File(vcBin, exe)
        if (candidate.exists()) {
            return candidate
        }
        throw new RuntimeException("dumpbin.exe not found")
    }

    private String getDumpbinHeaders() {
        def dumpbin = findExe("dumpbin.exe")
        def process = [dumpbin.absolutePath, '/HEADERS', binaryFile.absolutePath].execute(["PATH=$vcBin;$commonBin"], null)
        return process.inputStream.text
    }

    def static readArch(def input) {
        def pattern = /(?m)^.* machine \((.*)\).*$/
        return (input =~ pattern).findResult { line, group ->
            group.trim()
        }
    }

    ArchitectureInternal getArch() {
        def archString = readArch(dumpbinHeaders)
        switch (archString) {
            case "x86":
                return new DefaultArchitecture("x86", ArchitectureInternal.InstructionSet.X86, 32)
            case "x64":
                return new DefaultArchitecture("x86_64", ArchitectureInternal.InstructionSet.X86, 64)
            case "IA64":
                return new DefaultArchitecture("ia-64", ArchitectureInternal.InstructionSet.ITANIUM, 64)
            default:
                throw new RuntimeException("Cannot determine architecture for ${archString}")
        }
    }

    List<String> listObjectFiles() {
        def dumpbin = findExe("lib.exe")
        def process = [dumpbin.absolutePath, '/LIST', binaryFile.absolutePath].execute(["PATH=$vcBin;$commonBin"], null)
        return process.inputStream.readLines().drop(3).collect { new File(it).name }
    }
}
