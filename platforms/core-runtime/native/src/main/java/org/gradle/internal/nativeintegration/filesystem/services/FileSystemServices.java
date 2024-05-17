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
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.internal.file.StatStatistics;
import org.gradle.internal.nativeintegration.filesystem.FileCanonicalizer;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessor;
import org.gradle.internal.nativeintegration.filesystem.FileModeAccessor;
import org.gradle.internal.nativeintegration.filesystem.FileModeMutator;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.filesystem.Symlink;
import org.gradle.internal.nativeintegration.filesystem.jdk7.Jdk7Symlink;
import org.gradle.internal.nativeintegration.filesystem.jdk7.WindowsJdk7Symlink;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.service.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.gradle.internal.nativeintegration.filesystem.services.JdkFallbackHelper.newInstanceOrFallback;

public class FileSystemServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemServices.class);

    public void configure(ServiceRegistration registration) {
        registration.add(GenericFileSystem.Factory.class);
        registration.add(StatStatistics.Collector.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    public FileCanonicalizer createFileCanonicalizer() {
        return newInstanceOrFallback("org.gradle.internal.nativeintegration.filesystem.jdk7.Jdk7FileCanonicalizer", FileSystemServices.class.getClassLoader(), FallbackFileCanonicalizer.class);
    }

    private static Symlink createWindowsJdkSymlink() {
        if (JavaVersion.current().isJava7Compatible()) {
            return new WindowsJdk7Symlink();
        } else {
            return new WindowsSymlink();
        }
    }

    private static Symlink createJdkSymlink(TemporaryFileProvider temporaryFileProvider) {
        if (JavaVersion.current().isJava7Compatible()) {
            return new Jdk7Symlink(temporaryFileProvider);
        } else {
            return new UnsupportedSymlink();
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public FileSystem createFileSystem(
        GenericFileSystem.Factory genericFileSystemFactory,
        OperatingSystem operatingSystem,
        PosixFiles posixFiles,
        FileMetadataAccessor metadataAccessor,
        TemporaryFileProvider temporaryFileProvider
    ) {
        if (operatingSystem.isWindows()) {
            final Symlink symlink = createWindowsJdkSymlink();
            return genericFileSystemFactory.create(new EmptyChmod(), new FallbackStat(), symlink);
        }

        if (posixFiles instanceof UnavailablePosixFiles) {
            LOGGER.debug("Native-platform file system integration is not available. Continuing with fallback.");
        } else {
            Symlink symlink = new NativePlatformBackedSymlink(posixFiles);
            FileModeMutator chmod = new NativePlatformBackedChmod(posixFiles);
            FileModeAccessor stat = new NativePlatformBackedStat(posixFiles);
            return genericFileSystemFactory.create(chmod, stat, symlink);
        }

        Symlink symlink = createJdkSymlink(temporaryFileProvider);
        LOGGER.debug("Using {} implementation as symlink.", symlink.getClass().getSimpleName());

        // Use java 7 APIs, if available, otherwise fallback to no-op
        Object handler = newInstanceOrFallback("org.gradle.internal.nativeintegration.filesystem.jdk7.PosixJdk7FilePermissionHandler", FileSystemServices.class.getClassLoader(), UnsupportedFilePermissions.class);
        return genericFileSystemFactory.create((FileModeMutator) handler, (FileModeAccessor) handler, symlink);
    }

}
