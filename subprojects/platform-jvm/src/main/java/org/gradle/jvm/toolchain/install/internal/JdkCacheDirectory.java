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
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class JdkCacheDirectory {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdkCacheDirectory.class);

    private final FileOperations operations;
    private final File jdkDirectory;

    public JdkCacheDirectory(GradleUserHomeDirProvider homeDirProvider, FileOperations operations) {
        this.operations = operations;
        this.jdkDirectory = new File(homeDirProvider.getGradleUserHomeDirectory(), "jdks");
    }

    public Set<File> listJavaHomes() {
        final File[] files = jdkDirectory.listFiles();
        if (files != null) {
            return Arrays.stream(files).filter(File::isDirectory).map(this::findJavaHome).collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    private File findJavaHome(File potentialHome) {
        if (new File(potentialHome, "Contents/Home").exists()) {
            return new File(potentialHome, "Contents/Home");
        }
        return potentialHome;
    }

    /**
     * Unpacks and installs the given JDK archive. Returns a file pointing to the java home directory.
     */
    public File provisionFromArchive(File jdkArchive) {
        return findJavaHome(unpack(jdkArchive));
    }

    private File unpack(File jdkArchive) {
        final FileTree fileTree = asFileTree(jdkArchive);
        operations.copy(spec -> {
            spec.from(fileTree);
            spec.into(jdkDirectory);
        });
        final String rootName = getRootDirectory(fileTree);
        final File installLocation = new File(jdkDirectory, rootName);
        LOGGER.info("Installed {} into {}", jdkArchive.getName(), installLocation);
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

}
