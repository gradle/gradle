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
import com.sun.jna.Native;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.nativeplatform.jna.LibC;
import org.gradle.internal.os.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class FilePermissionHandlerFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilePermissionHandlerFactory.class);

    public static FilePermissionHandler createDefaultFilePermissionHandler() {
        if (Jvm.current().isJava7() && !OperatingSystem.current().isWindows()) {
            try {
                String jdkFilePermissionclass = "org.gradle.internal.nativeplatform.filesystem.jdk7.PosixJdk7FilePermissionHandler";
                FilePermissionHandler jdk7FilePermissionHandler =
                        (FilePermissionHandler) FilePermissionHandler.class.getClassLoader().loadClass(jdkFilePermissionclass).newInstance();
                return jdk7FilePermissionHandler;
            } catch (Exception e) {
                LOGGER.warn("Unable to load Jdk7FilePermissionHandler", e);
            }
        }
        ComposableFilePermissionHandler.Chmod chmod = createChmod();
        return new ComposableFilePermissionHandler(chmod, PosixUtil.current());
    }

    static ComposableFilePermissionHandler.Chmod createChmod() {
        try {
            LibC libc = (LibC) Native.loadLibrary("c", LibC.class);
            return new LibcChmod(libc);
        } catch (LinkageError e) {
            return new EmptyChmod();
        }
    }

    private static class LibcChmod implements ComposableFilePermissionHandler.Chmod {
        private final LibC libc;

        public LibcChmod(LibC libc) {
            this.libc = libc;
        }

        public void chmod(File f, int mode) throws IOException {
            try{
                libc.chmod(f.getAbsolutePath(), mode);
            }catch(LastErrorException exception){
                throw new IOException(String.format("Failed to set file permissions %s on file %s. errno: %d", mode, f.getName(), exception.getErrorCode()));
            }
        }
    }

    private static class EmptyChmod implements ComposableFilePermissionHandler.Chmod {
        public void chmod(File f, int mode) throws IOException {
        }
    }
}
