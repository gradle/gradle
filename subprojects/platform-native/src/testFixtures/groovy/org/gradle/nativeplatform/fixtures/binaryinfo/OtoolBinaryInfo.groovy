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

import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.Architectures

class OtoolBinaryInfo implements BinaryInfo {
    def binaryFile

    OtoolBinaryInfo(File binaryFile) {
        this.binaryFile = binaryFile
    }

    ArchitectureInternal getArch() {
        def process = ['otool', '-hv', binaryFile.absolutePath].execute()
        def lines = process.inputStream.readLines()
        def archString = lines.last().split()[1]

        switch (archString) {
            case "I386":
                return Architectures.forInput("x86")
            case "X86_64":
                return Architectures.forInput("x86_64")
            default:
                throw new RuntimeException("Cannot determine architecture for ${archString}")
        }
    }

    List<String> listObjectFiles() {
        def process = ['ar', '-t', binaryFile.getAbsolutePath()].execute()
        return process.inputStream.readLines().drop(1)
    }

    List<String> listLinkedLibraries() {
        def process = ['otool', '-L', binaryFile.absolutePath].execute()
        def lines = process.inputStream.readLines()
        return lines
    }

    String getSoName() {
        def process = ['otool', '-D', binaryFile.absolutePath].execute()
        def lines = process.inputStream.readLines()
        return lines[1]
    }
}
