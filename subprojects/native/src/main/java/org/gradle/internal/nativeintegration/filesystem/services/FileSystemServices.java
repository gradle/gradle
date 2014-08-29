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

package org.gradle.internal.nativeintegration.filesystem.services;

import net.rubygrapefruit.platform.NativeIntegrationUnavailableException;
import net.rubygrapefruit.platform.PosixFiles;
import org.gradle.api.JavaVersion;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.nativeintegration.filesystem.FileCanonicalizer;
import org.gradle.internal.nativeintegration.filesystem.FileModeAccessor;
import org.gradle.internal.nativeintegration.filesystem.FileModeMutator;
import org.gradle.internal.nativeintegration.filesystem.Symlink;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.os.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileSystemServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemServices.class);

    @SuppressWarnings("UnusedDeclaration")
    public FileCanonicalizer createFileCanonicalizer() {
        return (FileCanonicalizer) newInstance("org.gradle.internal.nativeintegration.filesystem.jdk7.Jdk7FileCanonicalizer", FallbackFileCanonicalizer.class);
    }

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

        // Use java 7 APIs, if available, otherwise fallback to no-op
        Object handler = newInstance("org.gradle.internal.nativeintegration.filesystem.jdk7.PosixJdk7FilePermissionHandler", UnsupportedFilePermissions.class);
        return new GenericFileSystem((FileModeMutator) handler, (FileModeAccessor) handler, symlink);
    }

    private Object newInstance(String jdk7Type, Class<?> fallbackType) {
        // Use java 7 APIs, if available
        Class<?> handlerClass = null;
        if (JavaVersion.current().isJava7()) {
            try {
                handlerClass = FileSystemServices.class.getClassLoader().loadClass(jdk7Type);
                LOGGER.debug("Using JDK 7 file service {}", jdk7Type);
            } catch (ClassNotFoundException e) {
                // Ignore
            }
        }
        if (handlerClass == null) {
            LOGGER.debug("Unable to load {}. Continuing with fallback {}.", jdk7Type, fallbackType.getName());
            handlerClass = fallbackType;
        }
        try {
            return handlerClass.newInstance();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
