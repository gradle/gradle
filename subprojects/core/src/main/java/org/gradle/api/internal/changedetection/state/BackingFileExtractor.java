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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.file.collections.DefaultFileCollectionResolveContext;
import org.gradle.api.internal.file.collections.FileCollectionContainer;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.api.internal.file.collections.ResolvableFileCollectionResolveContext;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.file.PathToFileResolver;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Extracts directories (or single files) backing a file collection. This is meant to be used to find out the task input and task output directories.
 *
 * It's not possible to know whether a file is a single file or directory for task inputs or outputs. For this reason any logic shouldn't rely on a exact answer.
 */
class BackingFileExtractor {
    private final static Logger LOG = Logging.getLogger(BackingFileExtractor.class);

    public List<FileEntry> extractFilesOrDirectories(FileCollection fileCollection) {
        DefaultFileCollectionResolveContext context = new CustomFileCollectionResolveContext();
        context.add(fileCollection);
        List<FileCollectionInternal> fileCollections = context.resolveAsFileCollections();
        List<FileEntry> results = new ArrayList<FileEntry>();
        for (FileCollectionInternal files : fileCollections) {
            collectBackingFiles(files, results);
        }
        return results;
    }

    private void collectBackingFiles(FileCollectionInternal fileCollection,
                                     final List<FileEntry> results) {
        if (fileCollection instanceof FileTreeAdapter) {
            collectBackingFilesInMinimalFileTree(((FileTreeAdapter) fileCollection).getTree(), results);
        } else {
            FileSystemSubset.Builder watchPointsBuilder = FileSystemSubset.builder();
            fileCollection.registerWatchPoints(watchPointsBuilder);
            for (File root : watchPointsBuilder.build().getRoots()) {
                results.add(new FileEntry(root));
            }
        }
    }

    private void collectBackingFilesInMinimalFileTree(MinimalFileTree fileTree, final List<FileEntry> results) {
        if (fileTree instanceof DirectoryTree) {
            DirectoryTree directoryTree = (DirectoryTree) fileTree;
            results.add(new FileEntry(directoryTree.getDir(), directoryTree.getPatterns()));
        } else {
            fileTree.visitTreeOrBackingFile(new FileVisitor() {
                @Override
                public void visitDir(FileVisitDetails dirDetails) {
                    results.add(new FileEntry(dirDetails.getFile()));
                }

                @Override
                public void visitFile(FileVisitDetails fileDetails) {
                    results.add(new FileEntry(fileDetails.getFile()));
                }
            });
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

    // ResolvableFileCollectionResolveContext implementation that doesn't initialize LazilyInitializedFileCollection instances
    private static class CustomFileCollectionResolveContext extends DefaultFileCollectionResolveContext {
        public CustomFileCollectionResolveContext() {
            this(new IdentityFileResolver());
        }

        CustomFileCollectionResolveContext(FileResolver fileResolver) {
            this(fileResolver, new CustomFileCollectionConverter(), new FileTreeConverter(fileResolver.getPatternSetFactory()));
        }

        CustomFileCollectionResolveContext(PathToFileResolver fileResolver, Converter<? extends FileCollectionInternal> fileCollectionConverter, Converter<? extends FileTreeInternal> fileTreeConverter) {
            super(fileResolver, fileCollectionConverter, fileTreeConverter);
        }

        @Override
        protected <T> void resolveNested(FileCollectionContainer fileCollection, List<T> result, Converter<? extends T> converter) {
            if (fileCollection instanceof LazilyInitializedFileCollection) {
                // don't initialize LazilyInitializedFileCollection
                converter.convertInto(fileCollection, result, fileResolver);
            } else {
                super.resolveNested(fileCollection, result, converter);
            }
        }

        @Override
        protected ResolvableFileCollectionResolveContext newContext(PathToFileResolver fileResolver) {
            return new CustomFileCollectionResolveContext(fileResolver, fileCollectionConverter, fileTreeConverter);
        }
    }

    private static class CustomFileCollectionConverter extends DefaultFileCollectionResolveContext.FileCollectionConverter {
        @Override
        public void convertInto(Object element, Collection<? super FileCollectionInternal> result, PathToFileResolver fileResolver) {
            if (shouldIgnoreWhenExtractingBackingFiles(element)) {
                return;
            }
            super.convertInto(element, result, fileResolver);
        }
    }

    private static boolean shouldIgnoreWhenExtractingBackingFiles(Object element) {
        if (element instanceof Configuration) {
            // ignore configurations
            return true;
        }
        if (element instanceof LazilyInitializedFileCollection) {
            // ignore LazilyInitializedFileCollection instances
            return true;
        }
        return false;
    }
}
