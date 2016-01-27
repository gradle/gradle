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
import net.rubygrapefruit.platform.SystemInfo
import net.rubygrapefruit.platform.WindowsRegistry
import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.Architectures
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.nativeplatform.toolchain.internal.msvcpp.DefaultVisualStudioLocator
import org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualStudioInstall

class DumpbinBinaryInfo implements BinaryInfo {
    final File binaryFile
    final File vcBin
    final String vcPath

    DumpbinBinaryInfo(File binaryFile) {
        this.binaryFile = binaryFile

        VisualStudioInstall vsInstall = findVisualStudio()
        DefaultNativePlatform targetPlatform = new DefaultNativePlatform("default");
        vcBin = vsInstall.getVisualCpp().getBinaryPath(targetPlatform)
        vcPath = vsInstall.getVisualCpp().getPath(targetPlatform).join(';')
    }

    static VisualStudioInstall findVisualStudio() {
        def vsLocator = new DefaultVisualStudioLocator(OperatingSystem.current(), NativeServices.instance.get(WindowsRegistry), NativeServices.instance.get(SystemInfo))
        return vsLocator.locateVisualStudioInstalls(null).visualStudio
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

    String getSoName() {
        throw new UnsupportedOperationException("soname is not relevant on windows")
    }
}
