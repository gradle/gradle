/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.nativeplatform.filesystem;

import com.sun.jna.WString;
import org.gradle.internal.nativeplatform.jna.LibC;

import java.io.File;

class DefaultFilePathEncoder implements FilePathEncoder {
    private final LibC libC;

    DefaultFilePathEncoder(LibC libC) {
        this.libC = libC;
    }

    public byte[] encode(File file) {
        byte[] path = new byte[file.getAbsolutePath().length() * 3 + 1];
        int pathLength = libC.wcstombs(path, new WString(file.getAbsolutePath()), path.length);
        if (pathLength < 0) {
            throw new RuntimeException(String.format("Could not encode file path '%s'.", file.getAbsolutePath()));
        }
        byte[] zeroTerminatedByteArray = new byte[pathLength + 1];
        System.arraycopy(path, 0, zeroTerminatedByteArray, 0, pathLength);
        zeroTerminatedByteArray[pathLength] = 0;
        return zeroTerminatedByteArray;
    }
}
