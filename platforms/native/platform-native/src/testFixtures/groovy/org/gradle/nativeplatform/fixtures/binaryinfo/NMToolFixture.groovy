/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.internal.os.OperatingSystem

class NMToolFixture {
    private final List<String> environments

    private NMToolFixture(List<String> environments) {
        this.environments = environments
    }

    static NMToolFixture of(List<String> environments) {
        return new NMToolFixture(environments)
    }

    private findExe(String exe) {
        // *nix OS correctly handle search inside the process's PATH environment variable
        if (!OperatingSystem.current().windows) {
            return [exe]
        }

        // Windows need to use cmd /c to correctly search inside the process's PATH environment variable
        return ["cmd.exe", "/d", "/c", exe]
    }

    List<BinaryInfo.Symbol> listSymbols(File binaryFile) {
        def process = (findExe('nm') + ['-a', '-f', 'posix', binaryFile.absolutePath]).execute(environments, null)
        def lines = process.inputStream.readLines()
        def result = []
        for (final line in lines) {
            // Looks like on Linux:
            // _main t 0 0
            //
            // Looks like on Windows (MinGW):
            // main T 0000000000401550
            def splits = line.split(' ').toList()

            // The symbol name is the first element, but is not delimited so if it contains spaces, it will span multiple elements
            String name
            if (splits.size() > 4) {
                def nameElements = splits.subList(0, splits.size() - 3)
                name = nameElements.join(" ")
                nameElements.clear()
            } else {
                name = splits[0]
                splits.remove(0)
            }

            if (name.isEmpty()) {
                // Name is blank when the symbol is undefined
                continue
            }

            if (splits.isEmpty()) {
                // Can get entries that contain just the symbol name
                result << new BinaryInfo.Symbol(name, '?' as char, false)
            } else {
                char type = splits[0].getChars()[0]
                result << new BinaryInfo.Symbol(name, type, Character.isUpperCase(type))
            }
        }
        return result
    }
}
