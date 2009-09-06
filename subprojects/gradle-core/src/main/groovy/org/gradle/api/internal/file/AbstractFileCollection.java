/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.*;

public abstract class AbstractFileCollection implements FileCollection {
    /**
     * Returns the display name of this file collection. Used in log and error messages.
     *
     * @return the display name
     */
    public abstract String getDisplayName();

    @Override
    public String toString() {
        return getDisplayName();
    }

    public File getSingleFile() throws IllegalStateException {
        Collection<File> files = getFiles();
        if (files.isEmpty()) {
            throw new IllegalStateException(String.format(
                    "Expected %s to contain exactly one file, however, it contains no files.", getDisplayName()));
        }
        if (files.size() != 1) {
            throw new IllegalStateException(String.format(
                    "Expected %s to contain exactly one file, however, it contains %d files.", getDisplayName(),
                    files.size()));
        }
        return files.iterator().next();
    }

    public Iterator<File> iterator() {
        return getFiles().iterator();
    }

    public String getAsPath() {
        return GUtil.join(getFiles(), File.pathSeparator);
    }

    public FileCollection plus(FileCollection collection) {
        return new UnionFileCollection(this, collection);
    }

    public FileCollection add(FileCollection collection) throws UnsupportedOperationException {
        throw new UnsupportedOperationException(String.format("%s does not allow modification.", getCapDisplayName()));
    }

    public Object addToAntBuilder(Object node, String childNodeName) {
        new AntFileCollectionBuilder(this).addToAntBuilder(node, childNodeName);
        return this;
    }

    public FileCollection stopExecutionIfEmpty() {
        if (getFiles().isEmpty()) {
            throw new StopExecutionException(String.format("%s does not contain any files.", getCapDisplayName()));
        }
        return this;
    }

    public Object asType(Class<?> type) throws UnsupportedOperationException {
        if (type.isAssignableFrom(Set.class)) {
            return getFiles();
        }
        if (type.isAssignableFrom(List.class)) {
            return new ArrayList<File>(getFiles());
        }
        if (type.isAssignableFrom(File[].class)) {
            Set<File> files = getFiles();
            return files.toArray(new File[files.size()]);
        }
        if (type.isAssignableFrom(File.class)) {
            return getSingleFile();
        }
        throw new UnsupportedOperationException(String.format("Cannot convert %s to type %s.", getDisplayName(),
                type.getSimpleName()));
    }

    public FileTree getAsFileTree() {
        throw new UnsupportedOperationException();
    }

    protected String getCapDisplayName() {
        return StringUtils.capitalize(getDisplayName());
    }
}
