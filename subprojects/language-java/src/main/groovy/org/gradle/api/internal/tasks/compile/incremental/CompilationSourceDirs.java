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

import org.gradle.api.file.SourceDirectorySet;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class CompilationSourceDirs {

    private List<Object> source;

    public CompilationSourceDirs(List<Object> source) {
        this.source = source;
    }

    List<File> getSourceDirs() {
        List<File> sourceDirs = new LinkedList<File>();
        for (Object s : source) {
            if (s instanceof SourceDirectorySet) {
                sourceDirs.addAll(((SourceDirectorySet) s).getSrcDirs());
            } else {
                throw new UnsupportedOperationException();
            }
        }
        return sourceDirs;
    }

    public boolean areSourceDirsKnown() {
        for (Object s : source) {
            if (!(s instanceof SourceDirectorySet)) {
                return false;
            }
        }
        return true;
    }
}
