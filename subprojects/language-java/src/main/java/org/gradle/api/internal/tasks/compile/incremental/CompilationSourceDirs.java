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

import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.SourceDirectorySet;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * Attempts to infer the source root directories for the `source` inputs to a
 * {@link org.gradle.api.tasks.compile.JavaCompile} task, in order to determine the `.class` file that corresponds
 * to any input source file.
 * 
 * This is a bit of a hack: we'd be better off inspecting the actual source file to determine the name of the class file.
 */
public class CompilationSourceDirs {

    private List<Object> source;

    public CompilationSourceDirs(List<Object> source) {
        this.source = source;
    }

    List<File> getSourceDirs() {
        List<File> sourceDirs = new LinkedList<File>();
        for (Object s : source) {
            if (s instanceof File) {
                sourceDirs.add((File) s);
            } else if (s instanceof DirectoryTree) {
                sourceDirs.add(((DirectoryTree) s).getDir());
            } else if (s instanceof SourceDirectorySet) {
                sourceDirs.addAll(((SourceDirectorySet) s).getSrcDirs());
            } else {
                throw new UnsupportedOperationException();
            }
        }
        return sourceDirs;
    }

    public boolean canInferSourceDirectories() {
        for (Object s : source) {
            if (!canInferSourceDirectory(s)) {
                return false;
            }
        }
        return true;
    }

    private boolean canInferSourceDirectory(Object s) {
        return s instanceof SourceDirectorySet
                || s instanceof DirectoryTree
                || s instanceof File;
    }
}
