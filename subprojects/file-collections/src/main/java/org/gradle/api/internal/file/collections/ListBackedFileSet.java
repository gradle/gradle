/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.file.collections;

import com.google.common.collect.ImmutableSet;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Set;

/**
 * Adapts a java util collection into a file set.
 */
public class ListBackedFileSet implements MinimalFileSet {
    private final ImmutableSet<File> files;

    public ListBackedFileSet(ImmutableSet<File> files) {
        this.files = files;
    }

    @Override
    public String getDisplayName() {
        switch (files.size()) {
            case 0:
                return "empty file collection";
            case 1:
                return String.format("file '%s'", files.iterator().next());
            default:
                return String.format("files %s", GUtil.toString(files));
        }
    }

    @Override
    public Set<File> getFiles() {
        return files;
    }
}
