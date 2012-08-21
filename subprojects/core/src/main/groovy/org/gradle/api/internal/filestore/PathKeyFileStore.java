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
 * File store that accepts the target path as the key for the entry.
 *
 * There is always at most one entry for a given key for this file store. If an entry already exists at
 * the given path, it will be overwritten. Paths can contain directory components, which will be created on demand.
 *
 * This file store also provides searching via relative ant path patterns.
 */
public class PathKeyFileStore implements FileStore<String>, FileStoreSearcher<String> {

    private final Random generator = new Random(System.currentTimeMillis());

    private final File baseDir;

    public PathKeyFileStore(File baseDir) {
        this.baseDir = baseDir;
    }

    public File getBaseDir() {
        return baseDir;
    }

    public FileStoreEntry add(String path, File source) {
        File destination = getFile(path);
        saveIntoFileStore(source, destination);
        return new DefaultFileStoreEntry<String>(destination);
    }

    private File getFile(String path) {
        return new File(baseDir, path);
    }

    public File getTempFile() {
        long tempLong = generator.nextLong();
        tempLong = tempLong < 0 ? -tempLong : tempLong;
        return new File(baseDir, "temp/" + tempLong);
    }

    protected void saveIntoFileStore(File source, File destination) {
        File parentDir = destination.getParentFile();
        if (!parentDir.mkdirs() && !parentDir.exists()) {
            throw new GradleException(String.format("Unable to create filestore directory %s", parentDir));
        }
        if (!source.renameTo(destination)) {
            throw new GradleException(String.format("Failed to copy file '%s' into filestore at '%s' ", source, destination));
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
