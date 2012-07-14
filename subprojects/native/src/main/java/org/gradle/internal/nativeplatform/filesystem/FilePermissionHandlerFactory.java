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
import com.sun.jna.WString;
import org.gradle.api.JavaVersion;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.nativeplatform.jna.LibC;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
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
    private static final ServiceRegistry SERVICES;

    static {
        DefaultServiceRegistry serviceRegistry = new DefaultServiceRegistry();
        addServices(serviceRegistry);
        SERVICES = serviceRegistry;
    }

    private static void addServices(DefaultServiceRegistry serviceRegistry) {
        FilePermissionHandler permissionHandler = createDefaultFilePermissionHandler();
        serviceRegistry.add(Chmod.class, permissionHandler);
        serviceRegistry.add(Stat.class, permissionHandler);
    }

    public static ServiceRegistry getServices() {
        return SERVICES;
    }

    public static FilePermissionHandler createDefaultFilePermissionHandler() {
        if (OperatingSystem.current().isWindows()) {
            return new ComposableFilePermissionHandler(new EmptyChmod(), new FallbackStat());
        }
        if (JavaVersion.current().isJava7()) {
            String jdkFilePermissionclass = "org.gradle.internal.nativeplatform.filesystem.jdk7.PosixJdk7FilePermissionHandler";
            try {
                return (FilePermissionHandler) FilePermissionHandler.class.getClassLoader().loadClass(jdkFilePermissionclass).newInstance();
            } catch (ClassNotFoundException e) {
                LOGGER.warn(String.format("Unable to load %s. Continuing with fallback.", jdkFilePermissionclass));
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
        Chmod chmod = createChmod();
        Stat stat = createStat();
        return new ComposableFilePermissionHandler(chmod, stat);
    }

    private static Stat createStat() {
        final OperatingSystem operatingSystem = OperatingSystem.current();
        if (operatingSystem.isLinux() || operatingSystem.isMacOsX()) {
            LibC libc = loadLibC();
            return new LibCStat(libc, operatingSystem, (BaseNativePOSIX) PosixUtil.current(), createEncoder(libc));
        } else {
            return new PosixStat(PosixUtil.current());
        }
    }

    static Chmod createChmod() {
        try {
            LibC libc = loadLibC();
            return new LibcChmod(libc, createEncoder(libc));
        } catch (LinkageError e) {
            LOGGER.debug("Unable to load LibC library. Falling back to EmptyChmod implementation.");
            return new EmptyChmod();
        }
    }

    static FilePathEncoder createEncoder(LibC libC) {
        if (OperatingSystem.current().isMacOsX()) {
            return new MacFilePathEncoder();
        }
        return new DefaultFilePathEncoder(libC);
    }

    private static class LibcChmod implements Chmod {
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

    private static class EmptyChmod implements Chmod {
        public void chmod(File f, int mode) throws IOException {
        }
    }

    static class LibCStat implements Stat {
        private final LibC libc;
        private final FilePathEncoder encoder;
        private final OperatingSystem operatingSystem;
        private final BaseNativePOSIX nativePOSIX;

        public LibCStat(LibC libc, OperatingSystem operatingSystem, BaseNativePOSIX nativePOSIX, FilePathEncoder encoder) {
            this.libc = libc;
            this.operatingSystem = operatingSystem;
            this.nativePOSIX = nativePOSIX;
            this.encoder = encoder;
        }

        public int getUnixMode(File f) throws IOException {
            FileStat stat = nativePOSIX.allocateStat();
            initPlatformSpecificStat(stat, encoder.encode(f));
            return stat.mode();
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

    private static class PosixStat implements Stat {
        private final POSIX posix;

        public PosixStat(POSIX posix) {
            this.posix = posix;
        }

        public int getUnixMode(File f) throws IOException {
            return this.posix.stat(f.getAbsolutePath()).mode();
        }
    }

    private static class FallbackStat implements Stat {
        public int getUnixMode(File f) throws IOException {
            if (f.isDirectory()) {
                return FileSystem.DEFAULT_DIR_MODE;
            } else {
                return FileSystem.DEFAULT_FILE_MODE;
            }
        }
    }

    private static LibC loadLibC() {
        return (LibC) Native.loadLibrary("c", LibC.class);
    }

    interface FilePathEncoder {
        byte[] encode(File file);
    }

    private static class MacFilePathEncoder implements FilePathEncoder {
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

    private static class DefaultFilePathEncoder implements FilePathEncoder {
        private final LibC libC;

        private DefaultFilePathEncoder(LibC libC) {
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
}

