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
import org.gradle.api.JavaVersion;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.nativeplatform.jna.LibC;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.jruby.ext.posix.BaseNativePOSIX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileSystemServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemServices.class);
    private static final ServiceRegistry SERVICES;

    static {
        DefaultServiceRegistry serviceRegistry = new DefaultServiceRegistry();
        addServices(serviceRegistry);
        SERVICES = serviceRegistry;
    }

    public static ServiceRegistry getServices() {
        return SERVICES;
    }

    private static void addServices(DefaultServiceRegistry serviceRegistry) {

        if (OperatingSystem.current().isWindows()) {
            serviceRegistry.add(Chmod.class, new EmptyChmod());
            serviceRegistry.add(Stat.class, new FallbackStat());
            serviceRegistry.add(Symlink.class, new FallbackSymlink());
            return;
        }

        serviceRegistry.add(Symlink.class, createSymlink());

        if (JavaVersion.current().isJava7()) {
            String jdkFilePermissionclass = "org.gradle.internal.nativeplatform.filesystem.jdk7.PosixJdk7FilePermissionHandler";
            try {
                FilePermissionHandler handler = (FilePermissionHandler) FilePermissionHandler.class.getClassLoader().loadClass(jdkFilePermissionclass).newInstance();
                serviceRegistry.add(FilePermissionHandler.class, handler);
                return;
            } catch (ClassNotFoundException e) {
                LOGGER.warn(String.format("Unable to load %s. Continuing with fallback.", jdkFilePermissionclass));
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        serviceRegistry.add(Chmod.class, createChmod());
        serviceRegistry.add(Stat.class, createStat());
    }

    private static Symlink createSymlink() {
        try {
            LibC libc = loadLibC();
            return new LibcSymlink(libc);
        } catch (LinkageError e) {
            LOGGER.debug("Unable to load LibC library. Falling back to FallbackSymlink implementation.");
            return new FallbackSymlink();
        }
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

    private static LibC loadLibC() {
        return (LibC) Native.loadLibrary("c", LibC.class);
    }

}

