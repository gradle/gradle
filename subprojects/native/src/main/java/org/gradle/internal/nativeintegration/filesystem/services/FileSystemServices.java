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

import net.rubygrapefruit.platform.file.PosixFiles;
import org.gradle.internal.nativeintegration.filesystem.FileCanonicalizer;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessor;
import org.gradle.internal.nativeintegration.filesystem.FileModeAccessor;
import org.gradle.internal.nativeintegration.filesystem.FileModeMutator;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.filesystem.Symlink;
import org.gradle.internal.os.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.gradle.internal.nativeintegration.filesystem.services.JdkFallbackHelper.newInstanceOrFallback;

public class FileSystemServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemServices.class);

    @SuppressWarnings("UnusedDeclaration")
    public FileCanonicalizer createFileCanonicalizer() {
        return newInstanceOrFallback("org.gradle.internal.nativeintegration.filesystem.jdk7.Jdk7FileCanonicalizer", FileSystemServices.class.getClassLoader(), FallbackFileCanonicalizer.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    public FileSystem createFileSystem(OperatingSystem operatingSystem, PosixFiles posixFiles, FileMetadataAccessor metadataAccessor) {

        if (operatingSystem.isWindows()) {
            Symlink symlink = newInstanceOrFallback("org.gradle.internal.nativeintegration.filesystem.jdk7.WindowsJdk7Symlink", FileSystemServices.class.getClassLoader(), WindowsSymlink.class);
            return new GenericFileSystem(new EmptyChmod(), new FallbackStat(), symlink, metadataAccessor);
        }

        if (posixFiles instanceof UnavailablePosixFiles) {
            LOGGER.debug("Native-platform file system integration is not available. Continuing with fallback.");
        } else {
            Symlink symlink = new NativePlatformBackedSymlink(posixFiles);
            FileModeMutator chmod = new NativePlatformBackedChmod(posixFiles);
            FileModeAccessor stat = new NativePlatformBackedStat(posixFiles);
            return new GenericFileSystem(chmod, stat, symlink, metadataAccessor);
        }

        Symlink symlink = newInstanceOrFallback("org.gradle.internal.nativeintegration.filesystem.jdk7.Jdk7Symlink", FileSystemServices.class.getClassLoader(), UnsupportedSymlink.class);
        LOGGER.debug("Using {} implementation as symlink.", symlink.getClass().getSimpleName());

        // Use java 7 APIs, if available, otherwise fallback to no-op
        Object handler = newInstanceOrFallback("org.gradle.internal.nativeintegration.filesystem.jdk7.PosixJdk7FilePermissionHandler", FileSystemServices.class.getClassLoader(), UnsupportedFilePermissions.class);
        return new GenericFileSystem((FileModeMutator) handler, (FileModeAccessor) handler, symlink, metadataAccessor);
    }

}
