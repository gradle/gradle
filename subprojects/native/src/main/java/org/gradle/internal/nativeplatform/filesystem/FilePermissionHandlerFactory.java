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
import org.gradle.api.JavaVersion;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.nativeplatform.jna.LibC;
import org.gradle.internal.os.OperatingSystem;
import org.jruby.ext.posix.BaseNativePOSIX;
import org.jruby.ext.posix.FileStat;
import org.jruby.ext.posix.Linux64FileStat;
import org.jruby.ext.posix.POSIX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class FilePermissionHandlerFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilePermissionHandlerFactory.class);

    public static FilePermissionHandler createDefaultFilePermissionHandler() {
        if (JavaVersion.current().isJava7() && !OperatingSystem.current().isWindows()) {
            String jdkFilePermissionclass = "org.gradle.internal.nativeplatform.filesystem.jdk7.PosixJdk7FilePermissionHandler";
            try {
                return (FilePermissionHandler) FilePermissionHandler.class.getClassLoader().loadClass(jdkFilePermissionclass).newInstance();
            } catch (ClassNotFoundException e) {
                LOGGER.warn(String.format("Unable to load %s. Continuing with fallback.", jdkFilePermissionclass));
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
        ComposableFilePermissionHandler.Chmod chmod = createChmod();
        ComposableFilePermissionHandler.Stat stat = createStat();
        return new ComposableFilePermissionHandler(chmod, stat);
    }

    private static ComposableFilePermissionHandler.Stat createStat() {
        final OperatingSystem operatingSystem = OperatingSystem.current();
        if (operatingSystem.isLinux() || operatingSystem.isMacOsX()) {
            LibC libc = loadLibC();
            return new LibCStat(libc, operatingSystem, (BaseNativePOSIX) PosixUtil.current());
        } else {
            return new PosixStat(PosixUtil.current());
        }
    }

    static ComposableFilePermissionHandler.Chmod createChmod() {
        try {
            LibC libc = loadLibC();
            return new LibcChmod(libc);
        } catch (LinkageError e) {
            LOGGER.debug("Unable to load LibC library. Falling back to EmptyChmod implementation.");
            return new EmptyChmod();
        }
    }


    private static class LibcChmod implements ComposableFilePermissionHandler.Chmod {
        private final LibC libc;

        public LibcChmod(LibC libc) {
            this.libc = libc;
        }

        public void chmod(File f, int mode) throws IOException {
            try {
                byte[] encodedFilePath = getEncodedFilePath(f);
                libc.chmod(encodedFilePath, mode);
            } catch (LastErrorException exception) {
                throw new IOException(String.format("Failed to set file permissions %s on file %s. errno: %d", mode, f.getName(), exception.getErrorCode()));
            }
        }
    }

    private static class EmptyChmod implements ComposableFilePermissionHandler.Chmod {
        public void chmod(File f, int mode) throws IOException {
        }
    }

    static class LibCStat implements ComposableFilePermissionHandler.Stat {
        private LibC libc;
        private OperatingSystem operatingSystem;
        private BaseNativePOSIX nativePOSIX;

        public LibCStat(LibC libc, OperatingSystem operatingSystem, BaseNativePOSIX nativePOSIX) {
            this.libc = libc;
            this.operatingSystem = operatingSystem;
            this.nativePOSIX = nativePOSIX;
        }

        public FileStat stat(File f) throws IOException {
            FileStat stat = nativePOSIX.allocateStat();
            initPlatformSpecificStat(stat, getEncodedFilePath(f));
            return stat;
        }

        private void initPlatformSpecificStat(FileStat stat, byte[] encodedFilePath) {
            if (operatingSystem.isMacOsX()) {
                libc.stat(encodedFilePath, stat);
            } else {
                final int statVersion = stat instanceof Linux64FileStat ? 3 : 0;
                libc.__xstat64(statVersion, encodedFilePath, stat);
            }
        }
    }

    private static class PosixStat implements ComposableFilePermissionHandler.Stat {
        private final POSIX posix;

        public PosixStat(POSIX posix) {
            this.posix = posix;
        }

        public FileStat stat(File f) throws IOException {
            return this.posix.stat(f.getAbsolutePath());
        }
    }

    private static LibC loadLibC() {
        return (LibC) Native.loadLibrary("c", LibC.class);
    }

    private static byte[] getEncodedFilePath(File f) {
        byte[] encoded = new byte[0];
        if (!OperatingSystem.current().isMacOsX()) {
            //macosx default file encoding is not unicode
            encoded = f.getAbsolutePath().getBytes();
        } else {
            try {
                encoded = f.getAbsolutePath().getBytes("utf-8");
            } catch (UnsupportedEncodingException e) {
                UncheckedException.throwAsUncheckedException(e);
            }
        }
        byte[] zeroTerminatedByteArray = new byte[encoded.length + 1];
        System.arraycopy(encoded, 0, zeroTerminatedByteArray, 0, encoded.length);
        zeroTerminatedByteArray[encoded.length] = 0;
        return zeroTerminatedByteArray;
    }
}

