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

import org.jruby.ext.posix.POSIX;

import java.io.File;
import java.io.IOException;

public class ComposableFilePermissionHandler implements FilePermissionHandler {
    private final Chmod chmod;
    private final POSIX posix;

    public ComposableFilePermissionHandler(Chmod chmod, POSIX posix) {
        this.chmod = chmod;
        this.posix = posix;
    }

    public int getUnixMode(File f) throws IOException {
        return posix.stat(f.getAbsolutePath()).mode() & 0777;
    }

    public void chmod(File f, int mode) throws IOException {
        chmod.chmod(f, mode);
    }

    public static interface Chmod {
        public void chmod(File f, int mode) throws IOException;

    }
}