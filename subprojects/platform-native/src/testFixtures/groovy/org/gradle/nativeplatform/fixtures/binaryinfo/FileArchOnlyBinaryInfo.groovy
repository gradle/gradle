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

import com.google.common.base.Preconditions
import com.google.common.collect.Iterables
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.Architectures

class FileArchOnlyBinaryInfo implements BinaryInfo {
    final File binaryFile

    FileArchOnlyBinaryInfo(File file) {
        this.binaryFile = Preconditions.checkNotNull(file)
    }

    @Override
    ArchitectureInternal getArch() {
        def process = ['file', binaryFile.absolutePath].execute()
        return readArch(process.inputStream.readLines())
    }

    @Override
    List<String> listObjectFiles() {
        throw new UnsupportedOperationException("Only getting the architecture is supported using the file utility")
    }

    @Override
    List<String> listLinkedLibraries() {
        throw new UnsupportedOperationException("Only getting the architecture is supported using the file utility")
    }

    @Override
    List<BinaryInfo.Symbol> listSymbols() {
        throw new UnsupportedOperationException("Only getting the architecture is supported using the file utility")
    }

    @Override
    List<BinaryInfo.Symbol> listDebugSymbols() {
        throw new UnsupportedOperationException("Only getting the architecture is supported using the file utility")
    }

    @Override
    String getSoName() {
        throw new UnsupportedOperationException("Only getting the architecture is supported using the file utility")
    }

    static ArchitectureInternal readArch(Collection<String> lines) {
        String header = Iterables.getFirst(lines, "")
        if (header.contains("x86-64")) {
            return Architectures.forInput("x86_64")
        } else if (header.contains("80386")) {
            return Architectures.forInput("x86")
        } else {
            throw new RuntimeException("Cannot determine architecture for ${header}\nfile output:\n${lines}")
        }
    }
}
