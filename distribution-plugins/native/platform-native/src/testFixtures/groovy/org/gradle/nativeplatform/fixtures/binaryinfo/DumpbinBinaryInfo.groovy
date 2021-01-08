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

package org.gradle.nativeplatform.fixtures.binaryinfo

import org.gradle.nativeplatform.fixtures.msvcpp.VisualStudioLocatorTestFixture
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.Architectures
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualStudioInstall

import javax.annotation.Nullable

class DumpbinBinaryInfo implements BinaryInfo {
    final File binaryFile
    final File vcBin
    final String vcPath

    DumpbinBinaryInfo(File binaryFile) {
        this.binaryFile = binaryFile

        VisualStudioInstall vsInstall = findVisualStudio()
        if (vsInstall == null) {
            throw new UnsupportedOperationException("Visual Studio is unavailable on this system.")
        }
        DefaultNativePlatform targetPlatform = new DefaultNativePlatform("default");
        def visualCpp = vsInstall.visualCpp.forPlatform(targetPlatform)
        vcBin = visualCpp.binDir
        vcPath = visualCpp.path.join(';')
    }

    static @Nullable VisualStudioInstall findVisualStudio() {
        return VisualStudioLocatorTestFixture.visualStudioLocator.locateComponent(null).component
    }

    private findExe(String exe) {
        final candidate = new File(vcBin, exe)
        if (candidate.exists()) {
            return candidate
        }
        throw new RuntimeException("dumpbin.exe not found")
    }

    protected String getDumpbinHeaders() {
        def dumpbin = findExe("dumpbin.exe")
        def process = [dumpbin.absolutePath, '/HEADERS', binaryFile.absolutePath].execute(["PATH=$vcPath"], null)
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
                return Architectures.forInput("x86")
            case "x64":
                return Architectures.forInput("x86_64")
            case "IA64":
                return Architectures.forInput("ia-64")
            case "ARM":
                return Architectures.forInput("arm")
            default:
                throw new RuntimeException("Cannot determine architecture for ${archString}")
        }
    }

    List<String> listObjectFiles() {
        def dumpbin = findExe("lib.exe")
        def process = [dumpbin.absolutePath, '/LIST', binaryFile.absolutePath].execute(["PATH=$vcPath"], null)
        return process.inputStream.readLines().drop(3).collect { new File(it).name }
    }

    List<String> listLinkedLibraries() {
        def dumpbin = findExe("dumpbin.exe")
        def process = [dumpbin.absolutePath, '/IMPORTS', binaryFile.absolutePath].execute(["PATH=$vcPath"], null)
        return process.inputStream.readLines()
    }

    List<BinaryInfo.Symbol> listSymbols() {
        def dumpbin = findExe("dumpbin.exe")
        def process = [dumpbin.absolutePath, '/SYMBOLS', binaryFile.absolutePath].execute(["PATH=$vcPath"], null)
        def lines = process.inputStream.readLines()
        return lines.findAll { it.contains(' | ') }.collect { line ->
            // Looks like:
            // 000 0105673E ABS    notype       Static       | @comp.id
            // 001 80000191 ABS    notype       Static       | @feat.00
            // 002 00000000 SECT1  notype       Static       | .drectve
            // 005 00000000 SECT2  notype       Static       | .debug$S
            // 008 00000000 SECT3  notype       Static       | .debug$T
            // 00B 00000000 SECT4  notype       Static       | .text$mn
            // 00E 00000000 SECT5  notype       Static       | .debug$S
            // 011 00000000 UNDEF  notype ()    External     | ?life@@YAHXZ (int __cdecl life(void))
            // 012 00000000 SECT4  notype ()    External     | _main
            // 013 00000000 SECT6  notype       Static       | .chks64
            def tokens = line.split('\\s+')
            def pipeIndex = tokens.findIndexOf { it == '|' }
            assert pipeIndex != -1
            return new BinaryInfo.Symbol(tokens[pipeIndex + 1], tokens[3].charAt(0), line.contains("External"))
        }
    }

    List<BinaryInfo.Symbol> listDebugSymbols() {
        throw new UnsupportedOperationException("Not yet implemented")
    }

    String getSoName() {
        throw new UnsupportedOperationException("soname is not relevant on windows")
    }
}
