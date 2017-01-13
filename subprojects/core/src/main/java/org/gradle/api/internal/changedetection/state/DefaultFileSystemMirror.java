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

import org.gradle.api.Nullable;
import org.gradle.api.internal.tasks.execution.TaskOutputsGenerationListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultFileSystemMirror implements FileSystemMirror, TaskOutputsGenerationListener {
    // Map from interned absolute path for a file to known details for the file. Currently not shared with trees
    private final Map<String, FileDetails> files = new ConcurrentHashMap<String, FileDetails>();
    // Map from interned absolute path for a directory to known details for the directory.
    private final Map<String, DirectoryTreeDetails> trees = new ConcurrentHashMap<String, DirectoryTreeDetails>();

    @Override
    public void beforeTaskOutputsGenerated() {
        // When the task outputs are generated, throw away all cached state. This is intentionally very simple, to be improved later
        files.clear();
        trees.clear();
    }

    @Nullable
    @Override
    public FileDetails getFile(String path) {
        return files.get(path);
    }

    @Override
    public void putFile(FileDetails file) {
        files.put(file.getPath(), file);
    }

    @Nullable
    @Override
    public DirectoryTreeDetails getDirectoryTree(String path) {
        return trees.get(path);
    }

    @Override
    public void putDirectory(DirectoryTreeDetails directory) {
        trees.put(directory.path, directory);
    }
}
