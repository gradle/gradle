/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.filewatch;

import com.google.common.collect.ImmutableList;
import org.gradle.api.file.DirectoryTree;

import java.io.File;
import java.util.List;

public class FileWatchInputs {
    private final ImmutableList<DirectoryTree> directories;
    private final ImmutableList<File> files;

    private FileWatchInputs(ImmutableList<DirectoryTree> directories, ImmutableList<File> files) {
        this.directories = directories;
        this.files = files;
    }

    public List<? extends DirectoryTree> getDirectoryTrees()  {
        return directories;
    }

    public List<? extends File> getFiles() { return files; }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private final ImmutableList.Builder<DirectoryTree> directories = ImmutableList.builder();
        private final ImmutableList.Builder<File> files = ImmutableList.builder();

        public Builder add(DirectoryTree directoryTree) {
            directories.add(directoryTree);
            return this;
        }

        public Builder add(File file) {
            files.add(file);
            return this;
        }

        public FileWatchInputs build() {
            return new FileWatchInputs(directories.build(), files.build());
        }
    }
}
