/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.install.internal;

import com.google.common.io.Files;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.internal.filelock.LockOptionsBuilder;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.os.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JdkCacheDirectory {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdkCacheDirectory.class);
    private static final String MARKER_FILE = "provisioned.ok";
    private static final String MAC_OS_JAVA_HOME_FOLDER = "Contents/Home";

    private final FileOperations operations;
    private final File jdkDirectory;
    private final FileLockManager lockManager;

    public JdkCacheDirectory(GradleUserHomeDirProvider homeDirProvider, FileOperations operations, FileLockManager lockManager) {
        this.operations = operations;
        this.jdkDirectory = new File(homeDirProvider.getGradleUserHomeDirectory(), "jdks");
        this.lockManager = lockManager;
        jdkDirectory.mkdir();
    }

    public Set<File> listJavaHomes() {
        final File[] candidates = jdkDirectory.listFiles();
        if (candidates != null) {
            return Arrays.stream(candidates)
                    .flatMap(this::markedLocations)
                    .map(this::getJavaHome)
                    .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    private Stream<File> markedLocations(File candidate) {
        if (isMarkedLocation(candidate)) {
            return Stream.of(candidate);
        }

        File[] subFolders = candidate.listFiles();
        if (subFolders == null) {
            return Stream.empty();
        }

        return Arrays.stream(subFolders).filter(this::isMarkedLocation);
    }

    private boolean isMarkedLocation(File candidate) {
        return candidate.isDirectory() && new File(candidate, MARKER_FILE).exists();
    }

    private File getJavaHome(File markedLocation) {
        if (OperatingSystem.current().isMacOsX()) {
            if (new File(markedLocation, MAC_OS_JAVA_HOME_FOLDER).exists()) {
                return new File(markedLocation, MAC_OS_JAVA_HOME_FOLDER);
            }

            File[] subfolders = markedLocation.listFiles(File::isDirectory);
            if (subfolders != null) {
                for(File subfolder : subfolders) {
                    if (new File(subfolder, MAC_OS_JAVA_HOME_FOLDER).exists()) {
                        return new File(subfolder, MAC_OS_JAVA_HOME_FOLDER);
                    }
                }
            }
        }

        return markedLocation;
    }

    /**
     * Unpacks and installs the given JDK archive. Returns a file pointing to the java home directory.
     */
    public File provisionFromArchive(File jdkArchive) {
        final File destination = unpack(jdkArchive);
        File markedLocation = markJavaHome(destination);
        return getJavaHome(markedLocation);
    }

    private File unpack(File jdkArchive) {
        final FileTree fileTree = asFileTree(jdkArchive);
        String installRootName = getNameWithoutExtension(jdkArchive);
        final File installLocation = new File(jdkDirectory, installRootName);
        if (!installLocation.exists()) {
            operations.copy(spec -> {
                spec.from(fileTree);
                spec.into(installLocation);
            });
            LOGGER.info("Installed {} into {}", jdkArchive.getName(), installLocation);
        }

        return installLocation;
    }

    private File markJavaHome(File installLocation) {
        File[] content = installLocation.listFiles();
        if (content == null) {
            //can't happen, the installation location is a directory, we have created it
            throw new RuntimeException("Programming error");
        }

        //mark the first directory since there should be only one
        for (File file : content) {
            if (file.isDirectory()) {
                markAsReady(file);
                return file;
            }
        }

        //there were no sub-directories in the installation location
        return markAsReady(installLocation);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private File markAsReady(File destination) {
        try {
            new File(destination, MARKER_FILE).createNewFile();
            return destination;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create .ok file", e);
        }
    }

    private FileTree asFileTree(File jdkArchive) {
        final String extension = FilenameUtils.getExtension(jdkArchive.getName());
        if (Objects.equals(extension, "zip")) {
            return operations.zipTree(jdkArchive);
        }
        return operations.tarTree(operations.getResources().gzip(jdkArchive));
    }

    public FileLock acquireWriteLock(File destinationFile, String operationName) {
        return lockManager.lock(destinationFile, LockOptionsBuilder.mode(FileLockManager.LockMode.Exclusive), destinationFile.getName(), operationName);
    }

    public File getDownloadLocation(String filename) {
        return new File(jdkDirectory, filename);
    }

    private static String getNameWithoutExtension(File file) {
        //remove all extensions, for example for xxx.tar.gz files only xxx should be left
        String output = file.getName();
        String input;
        do {
            input = output;
            output = Files.getNameWithoutExtension(input);
        } while (!input.equals(output));
        return output;
    }

}
