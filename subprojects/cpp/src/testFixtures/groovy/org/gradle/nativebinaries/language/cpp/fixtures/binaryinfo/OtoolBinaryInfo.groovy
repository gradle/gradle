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
import org.gradle.nativebinaries.internal.DefaultArchitecture

class OtoolBinaryInfo implements BinaryInfo {
    def binaryFile

    OtoolBinaryInfo(File binaryFile) {
        this.binaryFile = binaryFile    }

    ArchitectureInternal getArch() {
        def process = ['otool', '-hv', binaryFile.absolutePath].execute()
        def lines = process.inputStream.readLines()
        def archString = lines[3].split()[1]

        switch (archString) {
            case "I386":
                return new DefaultArchitecture("x86", ArchitectureInternal.InstructionSet.X86, 32)
            case "X86_64":
                return new DefaultArchitecture("x86_64", ArchitectureInternal.InstructionSet.X86, 64)
            default:
                throw new RuntimeException("Cannot determine architecture for ${archString}")
        }
    }

    List<String> listObjectFiles() {
        def process = ['ar', '-t', binaryFile.getAbsolutePath()].execute()
        return process.inputStream.readLines().drop(1)
    }
}
