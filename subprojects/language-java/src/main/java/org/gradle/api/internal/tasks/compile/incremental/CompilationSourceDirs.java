/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental;

import com.google.common.collect.Lists;
import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.logging.Logging;

import java.io.File;
import java.util.List;

/**
 * Attempts to infer the source root directories for the `source` inputs to a
 * {@link org.gradle.api.tasks.compile.JavaCompile} task, in order to determine the `.class` file that corresponds
 * to any input source file.
 * 
 * This is a bit of a hack: we'd be better off inspecting the actual source file to determine the name of the class file.
 */
public class CompilationSourceDirs {
    private static final org.gradle.api.logging.Logger LOG = Logging.getLogger(IncrementalCompilerDecorator.class);

    private final List<Object> sources;
    private List<File> sourceRoots;

    public CompilationSourceDirs(List<Object> sources) {
        this.sources = sources;
    }

    List<File> getSourceRoots() {
        if (sourceRoots == null) {
            sourceRoots = Lists.newArrayList();
            for (Object source : sources) {
                if (isDirectory(source)) {
                    sourceRoots.add((File) source);
                } else if (isDirectoryTree(source)) {
                    sourceRoots.add(((DirectoryTree) source).getDir());
                } else if (isSourceDirectorySet(source)) {
                    sourceRoots.addAll(((SourceDirectorySet) source).getSrcDirs());
                } else {
                    throw new UnsupportedOperationException();
                }
            }
        }
        return sourceRoots;
    }

    public boolean canInferSourceRoots() {
        for (Object source : sources) {
            if (!canInferSourceRoot(source)) {
                LOG.info("Cannot infer source root(s) for input with type `{}`. Supported types are `File`, `DirectoryTree` and `SourceDirectorySet`. Unsupported input: {}", source.getClass().getSimpleName(), source);
                return false;
            }
        }
        return true;
    }

    private boolean canInferSourceRoot(Object source) {
        return isSourceDirectorySet(source)
                || isDirectoryTree(source)
                || isDirectory(source);
    }

    private boolean isSourceDirectorySet(Object source) {
        return source instanceof SourceDirectorySet;
    }

    private boolean isDirectoryTree(Object source) {
        return source instanceof DirectoryTree;
    }

    private boolean isDirectory(Object source) {
        return source instanceof File
                && ((File) source).isDirectory();
    }
}
