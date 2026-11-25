/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.external.javadoc.internal;

import org.gradle.api.file.ConfigurableFileCollection;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A write-through {@code List<File>} view of a file collection, for eager getters that returned a mutable list.
 * Appending adds a source, so the sources declared before stay lazy.
 * Other changes replace the sources with the resolved files.
 */
@NullMarked
public final class FileListView extends AbstractList<File> {

    private final ConfigurableFileCollection files;

    public FileListView(ConfigurableFileCollection files) {
        this.files = files;
    }

    public static void setFiles(ConfigurableFileCollection files, @Nullable Iterable<? extends File> value) {
        if (value == null) {
            files.setFrom();
        } else if (value instanceof FileListView && ((FileListView) value).files == files) {
            // A collection that has itself as a source cannot be resolved
            files.setFrom(new ArrayList<>((FileListView) value));
        } else {
            files.setFrom(value);
        }
    }

    private List<File> resolved() {
        return new ArrayList<>(files.getFiles());
    }

    @Override
    public File get(int index) {
        return resolved().get(index);
    }

    @Override
    public int size() {
        return files.getFiles().size();
    }

    @Override
    public void add(int index, File element) {
        List<File> current = resolved();
        if (index == current.size()) {
            files.from(element);
        } else {
            current.add(index, element);
            files.setFrom(current);
        }
        modCount++;
    }

    @Override
    public boolean addAll(Collection<? extends File> elements) {
        files.from(new ArrayList<>(elements));
        modCount++;
        return !elements.isEmpty();
    }

    @Override
    public File set(int index, File element) {
        List<File> current = resolved();
        File previous = current.set(index, element);
        files.setFrom(current);
        return previous;
    }

    @Override
    public File remove(int index) {
        List<File> current = resolved();
        File previous = current.remove(index);
        files.setFrom(current);
        modCount++;
        return previous;
    }

    @Override
    public void clear() {
        files.setFrom();
        modCount++;
    }
}
