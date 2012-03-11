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

import com.sun.jna.Native;
import org.gradle.internal.nativeplatform.jna.LibC;

import java.io.File;
import java.io.IOException;

public class NativeFilePermissionHandler implements FilePermissionHandler {
    private FilePermissionHandler delegateHandler;
    private Chmod chmod;

    public NativeFilePermissionHandler(FilePermissionHandler filePermissionHandler) {
        this.delegateHandler = filePermissionHandler;
        try{
            LibC libc = (LibC) Native.loadLibrary("c", LibC.class);
            chmod = new LibcChmod(libc);
        }catch (Throwable e){
            chmod = new EmptyChmod();
        }
    }

    public int getUnixMode(File f) throws IOException {
        return delegateHandler.getUnixMode(f);
    }

    public void chmod(File f, int mode) throws IOException {
        chmod.chmod(f, mode);
    }

    private static interface Chmod {
        public void chmod(File f, int mode) throws IOException;
    }

    private class LibcChmod implements Chmod {
        private final LibC libc;

        public LibcChmod(LibC libc){
            this.libc = libc;
        }
        public void chmod(File f, int mode) {
            libc.chmod(f.getAbsolutePath(), mode);
        }
    }

    private class EmptyChmod implements Chmod {
        public void chmod(File f, int mode) throws IOException {
        }
    }
}