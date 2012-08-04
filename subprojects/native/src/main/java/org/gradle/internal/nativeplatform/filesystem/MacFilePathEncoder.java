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

import org.gradle.internal.UncheckedException;

import java.io.File;
import java.io.UnsupportedEncodingException;

class MacFilePathEncoder implements FilePathEncoder {
    public byte[] encode(File file) {
        byte[] encoded;
        try {
            encoded = file.getAbsolutePath().getBytes("utf-8");
        } catch (UnsupportedEncodingException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        byte[] zeroTerminatedByteArray = new byte[encoded.length + 1];
        System.arraycopy(encoded, 0, zeroTerminatedByteArray, 0, encoded.length);
        zeroTerminatedByteArray[encoded.length] = 0;
        return zeroTerminatedByteArray;
    }
}
