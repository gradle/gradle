/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.file;

import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.DefaultFileCollectionResolveContext;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts directories (or single files) backing a file collection. This is meant to be used to find out the task input and task output directories.
 *
 * It's not possible to know whether a file is a single file or directory for task inputs or outputs. For this reason any logic shouldn't rely on a exact answer.
 */
public class BackingFileExtractor {

    public List<FileEntry> extractFilesOrDirectories(FileCollection fileCollection) {
        DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext(
            new IdentityFileResolver());
        context.add(fileCollection);
        List<FileCollectionInternal> fileCollections = context.resolveAsFileCollections();
        List<FileEntry> results = new ArrayList<FileEntry>();
        for (FileCollectionInternal files : fileCollections) {
            collectDirectories(files, results);
        }
        return results;
    }

    private void collectDirectories(FileCollectionInternal fileCollection,
                                    List<FileEntry> results) {
        if (fileCollection instanceof FileTreeAdapter) {
            collectTree(((FileTreeAdapter) fileCollection).getTree(), results);
        } else {
            for (File file : fileCollection.getFiles()) {
                results.add(new FileEntry(file));
            }
        }
    }

    private void collectTree(MinimalFileTree fileTree, List<FileEntry> results) {
        if (fileTree instanceof DirectoryTree) {
            DirectoryTree directoryTree = (DirectoryTree) fileTree;
            results.add(new FileEntry(directoryTree.getDir(), directoryTree.getPatterns()));
        }
    }

    public static class FileEntry {
        private final File file;
        private final PatternSet patterns;

        public FileEntry(File file) {
            this.file = file;
            this.patterns = null;
        }

        public FileEntry(File dir, PatternSet patterns) {
            file = dir;
            this.patterns = patterns;
        }

        public File getFile() {
            return file;
        }

        public PatternSet getPatterns() {
            return patterns;
        }
    }
}
