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
        return ["cmd", "/c", exe]
    }

    List<BinaryInfo.Symbol> listSymbols(File binaryFile) {
        def process = (findExe('nm') + ['-a', '-f', 'posix', binaryFile.absolutePath]).execute(environments, null)
        def lines = process.inputStream.readLines()
        return lines.collect { line ->
            // Looks like on Linux:
            // _main t 0 0
            //
            // Looks like on Windows (MinGW):
            // main T 0000000000401550
            def splits = line.split(' ')
            String name = splits[0]
            char type = splits[1].getChars()[0]
            return new BinaryInfo.Symbol(name, type, Character.isUpperCase(type))
        }
    }
}
