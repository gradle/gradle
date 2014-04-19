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

import net.rubygrapefruit.platform.NativeIntegrationUnavailableException;
import net.rubygrapefruit.platform.PosixFiles;
import org.gradle.api.JavaVersion;
import org.gradle.internal.os.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileSystemServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemServices.class);

    @SuppressWarnings("UnusedDeclaration")
    public FileSystem createFileSystem(OperatingSystem operatingSystem) throws Exception {
        // Use no-op implementations for windows
        if (operatingSystem.isWindows()) {
            return new GenericFileSystem(new EmptyChmod(), new FallbackStat(), new WindowsSymlink());
        }

        // Use the native-platform integration, if available
        try {
            PosixFiles posixFiles = net.rubygrapefruit.platform.Native.get(PosixFiles.class);
            Symlink symlink = new NativePlatformBackedSymlink(posixFiles);
            FileModeMutator chmod = new NativePlatformBackedChmod(posixFiles);
            FileModeAccessor stat = new NativePlatformBackedStat(posixFiles);
            return new GenericFileSystem(chmod, stat, symlink);
        } catch (NativeIntegrationUnavailableException ex) {
            LOGGER.debug("Native-platform file system integration is not available. Continuing with fallback.");
        }

        LOGGER.debug("Using UnsupportedSymlink implementation.");
        Symlink symlink = new UnsupportedSymlink();

        // Use java 7 APIs, if available
        if (JavaVersion.current().isJava7()) {
            String jdkFilePermissionclass = "org.gradle.internal.nativeplatform.filesystem.jdk7.PosixJdk7FilePermissionHandler";
            Class<?> handlerClass = null;
            try {
                handlerClass = FileSystemServices.class.getClassLoader().loadClass(jdkFilePermissionclass);
            } catch (ClassNotFoundException e) {
                LOGGER.warn(String.format("Unable to load %s. Continuing with fallback.", jdkFilePermissionclass));
            }
            if (handlerClass != null) {
                LOGGER.debug("Using JDK 7 file services.");
                Object handler = handlerClass.newInstance();
                return new GenericFileSystem((FileModeMutator) handler, (FileModeAccessor) handler, symlink);
            }
        }

        // Fallback to no-op implementations if not available
        return new GenericFileSystem(new UnsupportedChmod(), new UnsupportedStat(), symlink);
    }
}
