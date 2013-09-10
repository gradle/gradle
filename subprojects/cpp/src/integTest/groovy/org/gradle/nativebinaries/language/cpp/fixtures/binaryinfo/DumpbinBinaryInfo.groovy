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

import org.gradle.nativebinaries.language.cpp.fixtures.AvailableToolChains.InstalledToolChain

class DumpbinBinaryInfo implements BinaryInfo {
    def output
    def archString

    DumpbinBinaryInfo(File binaryFile, InstalledToolChain tc) {
        def dumpbin = findDumpBin(tc.pathEntries)
        def process = [dumpbin.absolutePath, '/HEADERS', binaryFile.absolutePath].execute(tc.getRuntimeEnv(), null)
        output = process.inputStream.text
        archString = readArch(output)
    }

    static findDumpBin(List<File> pathEntries) {
        for (File pathEntry : pathEntries) {
            final candidate = new File(pathEntry, "dumpbin.exe")
            if (candidate.exists()) {
                return candidate
            }
        }
        throw new RuntimeException("dumpbin.exe not found")
    }

    def static readArch(def input) {
        def pattern = /(?m)^.* machine \((.*)\).*$/
        return (input =~ pattern).findResult { line, group ->
            group.trim()
        }
    }

    BinaryInfo.Architecture getArch() {
        switch (archString) {
            case "x86":
                return BinaryInfo.Architecture.I386
            case "x64":
                return BinaryInfo.Architecture.X86_64
            default:
                throw new RuntimeException("Cannot determine architecture for ${archString}")
        }
    }
}
