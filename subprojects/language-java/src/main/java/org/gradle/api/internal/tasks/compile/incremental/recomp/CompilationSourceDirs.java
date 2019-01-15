/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.recomp;

import com.google.common.collect.Lists;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionLeafVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;
import java.util.List;

/**
 * Attempts to infer the source root directories for the `source` inputs to a
 * {@link org.gradle.api.tasks.compile.JavaCompile} task, in order to determine the `.class` file that corresponds
 * to any input source file.
 *
 * This is a bit of a hack: we'd be better off inspecting the actual source file to determine the name of the class file.
 */
@NonNullApi
public class CompilationSourceDirs {
    private static final org.gradle.api.logging.Logger LOG = Logging.getLogger(CompilationSourceDirs.class);

    private final FileTreeInternal sources;
    private SourceRoots sourceRoots;

    public CompilationSourceDirs(FileTreeInternal sources) {
        this.sources = sources;
    }

    public List<File> getSourceRoots() {
        return resolveRoots().getSourceRoots();
    }

    public boolean canInferSourceRoots() {
        return resolveRoots().isCanInferSourceRoots();
    }

    private SourceRoots resolveRoots() {
        if (sourceRoots == null) {
            SourceRoots visitor = new SourceRoots();
            sources.visitLeafCollections(visitor);
            sourceRoots = visitor;
        }
        return sourceRoots;
    }

    private static class SourceRoots implements FileCollectionLeafVisitor {
        private boolean canInferSourceRoots = true;
        private List<File> sourceRoots = Lists.newArrayList();

        @Override
        public void visitCollection(FileCollectionInternal fileCollection) {
            cannotInferSourceRoots(fileCollection);
        }

        @Override
        public void visitGenericFileTree(FileTreeInternal fileTree) {
            cannotInferSourceRoots(fileTree);
        }

        @Override
        public void visitFileTree(File root, PatternSet patterns) {
            // We need to add missing files as source roots, since the package name for deleted files provided by IncrementalTaskInputs also need to be determined.
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

        public boolean isCanInferSourceRoots() {
            return canInferSourceRoots;
        }

        public List<File> getSourceRoots() {
            return sourceRoots;
        }
    }
}
