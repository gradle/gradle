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

import org.apache.commons.io.FilenameUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.internal.filelock.LockOptionsBuilder;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
        final File[] files = jdkDirectory.listFiles();
        if (files != null) {
            return Arrays.stream(files).filter(isValidInstallation()).map(this::findJavaHome).collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    private Predicate<File> isValidInstallation() {
        return home -> home.isDirectory() && new File(home, MARKER_FILE).exists();
    }

    private File findJavaHome(File potentialHome) {
        if (new File(potentialHome, MAC_OS_JAVA_HOME_FOLDER).exists()) {
            return new File(potentialHome, MAC_OS_JAVA_HOME_FOLDER);
        }

        File[] subfolders = potentialHome.listFiles(File::isDirectory);
        if (subfolders != null) {
            for(File subfolder : subfolders) {
                if (new File(subfolder, MAC_OS_JAVA_HOME_FOLDER).exists()) {
                    return new File(subfolder, MAC_OS_JAVA_HOME_FOLDER);
                }
            }
        }
        return potentialHome;
    }

    /**
     * Unpacks and installs the given JDK archive. Returns a file pointing to the java home directory.
     */
    public File provisionFromArchive(File jdkArchive) {
        final File destination = unpack(jdkArchive);
        markAsReady(destination);
        return findJavaHome(destination);
    }

    private void markAsReady(File destination) {
        try {
            new File(destination, MARKER_FILE).createNewFile();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create .ok file", e);
        }
    }

    private File unpack(File jdkArchive) {
        final FileTree fileTree = asFileTree(jdkArchive);
        final String rootName = getRootDirectory(fileTree);
        final File installLocation = new File(jdkDirectory, rootName);
        if (!installLocation.exists()) {
            operations.copy(spec -> {
                spec.from(fileTree);
                spec.into(jdkDirectory);
            });
            LOGGER.info("Installed {} into {}", jdkArchive.getName(), installLocation);
        }
        return installLocation;
    }

    private String getRootDirectory(FileTree fileTree) {
        AtomicReference<File> rootDir = new AtomicReference<>();
        fileTree.visit(new EmptyFileVisitor() {
            @Override
            public void visitDir(FileVisitDetails details) {
                rootDir.compareAndSet(null, details.getFile());
            }
        });
        return rootDir.get().getName();
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

}
