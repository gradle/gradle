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

package org.gradle.api.internal.tasks.compile;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.internal.RelativePathUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Relativizes paths relative to a set of source directories in order to create a platform-independent mapping
 * from source file to class file.
 */
@NonNullApi
public class CompilationSourceDirs {
    private static final Logger LOG = LoggerFactory.getLogger(CompilationSourceDirs.class);
    private final List<File> sourceRoots;

    public CompilationSourceDirs(JavaCompileSpec spec) {
        this.sourceRoots = new ArrayList<>(spec.getSourceRoots());
        File headerOutputDirectory = spec.getCompileOptions().getHeaderOutputDirectory();
        if (headerOutputDirectory != null) {
            sourceRoots.add(headerOutputDirectory);
        }
        File generatedSourcesDirectory = spec.getCompileOptions().getAnnotationProcessorGeneratedSourcesDirectory();
        if (generatedSourcesDirectory != null) {
            sourceRoots.add(generatedSourcesDirectory);
        }
    }

     @VisibleForTesting
    CompilationSourceDirs(List<File> sourceRoots) {
        this.sourceRoots = sourceRoots;
    }

    public static List<File> inferSourceRoots(FileTreeInternal sources) {
        SourceRoots visitor = new SourceRoots();
        sources.visitStructure(visitor);
        return visitor.canInferSourceRoots ? visitor.sourceRoots : Collections.emptyList();
    }

    /**
     * Calculate the relative path to the source root.
     */
    public Optional<String> relativize(File sourceFile) {
        return sourceRoots.stream()
            .filter(sourceDir -> sourceFile.getAbsolutePath().startsWith(sourceDir.getAbsolutePath()))
            .map(sourceDir -> RelativePathUtil.relativePath(sourceDir, sourceFile))
            .filter(relativePath -> !relativePath.startsWith(".."))
            .findFirst();
    }

    private static class SourceRoots implements FileCollectionStructureVisitor {
        private boolean canInferSourceRoots = true;
        private final List<File> sourceRoots = Lists.newArrayList();

        @Override
        public void visitCollection(FileCollectionInternal.Source source, Iterable<File> contents) {
            cannotInferSourceRoots(contents);
        }

        @Override
        public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
            cannotInferSourceRoots(fileTree);
        }

        @Override
        public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
            // We need to add missing files as source roots, since the package name for deleted files provided by InputChanges also need to be determined.
            if (!root.exists() || root.isDirectory()) {
                sourceRoots.add(root);
            } else {
                cannotInferSourceRoots("file '" + root + "'");
            }
        }

        private void cannotInferSourceRoots(Object fileCollection) {
            canInferSourceRoots = false;
            LOG.info("Cannot infer source root(s) for source `{}`. Supported types are `File` (directories only), `DirectoryTree` and `SourceDirectorySet`.", fileCollection);
        }
    }
}
