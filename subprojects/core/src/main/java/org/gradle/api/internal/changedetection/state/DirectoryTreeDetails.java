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

import java.util.List;

/**
 * Represents the state of a directory tree.
 */
public class DirectoryTreeDetails implements FileTreeSnapshot {
    // Interned path
    private final String path;
    // All elements, not just direct children
    private final List<FileSnapshot> elements;

    public DirectoryTreeDetails(String path, List<FileSnapshot> elements) {
        this.path = path;
        this.elements = elements;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public List<FileSnapshot> getDescendants() {
        return elements;
    }
}
