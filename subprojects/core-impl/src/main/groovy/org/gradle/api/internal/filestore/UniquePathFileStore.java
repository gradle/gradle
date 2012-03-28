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

package org.gradle.api.internal.filestore;

import org.gradle.api.GradleException;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * File store that stores items under a given path within a base directory.
 *
 * Paths are expected to be unique. If a request is given to store a file at a particular path
 * where a file exists already then it will not be copied. That is, it is expected to be equal.
 *
 * This file store also provides searching via relative ant path patterns.
 */
public class UniquePathFileStore implements FileStore<String>, FileStoreSearcher<String> {

    private final Random generator = new Random(System.currentTimeMillis());

    private final File baseDir;

    public UniquePathFileStore(File baseDir) {
        this.baseDir = baseDir;
    }

    public File getBaseDir() {
        return baseDir;
    }

    public File add(String path, File contentFile) {
        File destination = getFile(path);
        if (!destination.exists()) {
            saveIntoFileStore(contentFile, destination);
        }
        return destination;
    }

    private File getFile(String path) {
        return new File(baseDir, path);
    }

    public File getTempFile() {
        long tempLong = generator.nextLong();
        tempLong = tempLong < 0 ? -tempLong : tempLong;
        return new File(baseDir, "temp/" + tempLong);
    }

    private void saveIntoFileStore(File contentFile, File storageFile) {
        File parentDir = storageFile.getParentFile();
        if (!parentDir.mkdirs() && !parentDir.exists()) {
            throw new GradleException(String.format("Unabled to create filestore directory %s", parentDir));
        }
        if (!contentFile.renameTo(storageFile)) {
            throw new GradleException(String.format("Failed to copy downloaded content into storage file: %s", storageFile));
        }
    }

    public Set<? extends FileStoreEntry> search(String pattern) {
        final Set<DefaultFileStoreEntry> entries = new HashSet<DefaultFileStoreEntry>();
        findFiles(pattern).visit(new EmptyFileVisitor() {
            public void visitFile(FileVisitDetails fileDetails) {
                entries.add(new DefaultFileStoreEntry(fileDetails.getFile()));
            }
        });

        return entries;
    }
    
    private DirectoryFileTree findFiles(String pattern) {
        DirectoryFileTree fileTree = new DirectoryFileTree(baseDir);
        PatternFilterable patternSet = new PatternSet();
        patternSet.include(pattern);
        return fileTree.filter(patternSet);
    } 
}
