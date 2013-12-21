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

import com.sun.jna.LastErrorException;
import org.gradle.internal.nativeplatform.jna.LibC;

import java.io.File;
import java.io.IOException;

class LibcChmod implements Chmod {
    private final LibC libc;
    private final FilePathEncoder encoder;

    public LibcChmod(LibC libc, FilePathEncoder encoder) {
        this.libc = libc;
        this.encoder = encoder;
    }

    public void chmod(File f, int mode) throws IOException {
        try {
            byte[] encodedFilePath = encoder.encode(f);
            libc.chmod(encodedFilePath, mode);
        } catch (LastErrorException exception) {
            throw new IOException(String.format("Failed to set file permissions %s on file %s. errno: %d", mode, f.getName(), exception.getErrorCode()));
        }
    }
}
