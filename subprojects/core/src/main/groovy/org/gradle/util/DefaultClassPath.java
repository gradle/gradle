/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.util;

import java.io.File;
import java.io.Serializable;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class DefaultClassPath implements ClassPath, Serializable {
    private final List<File> files;

    public DefaultClassPath(Iterable<File> files) {
        this.files = new ArrayList<File>();
        for (File file : files) {
            this.files.add(file);
        }
    }
    
    public DefaultClassPath(File... files) {
        this(Arrays.asList(files));
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }

    public Collection<URI> getAsURIs() {
        return GFileUtils.toURIs(files);
    }

    public Collection<File> getAsFiles() {
        return files;
    }

    public URL[] getAsURLArray() {
        return GFileUtils.toURLArray(files);
    }

    public Collection<URL> getAsURLs() {
        return GFileUtils.toURLs(files);
    }

    public ClassPath plus(ClassPath other) {
        if (files.isEmpty()) {
            return other;
        }
        if (other.isEmpty()) {
            return this;
        }
        return new DefaultClassPath(concat(files, other.getAsFiles()));
    }

    public ClassPath plus(Collection<File> other) {
        if (other.isEmpty()) {
            return this;
        }
        return new DefaultClassPath(concat(files, other));
    }

    private Iterable<File> concat(List<File> files1, Collection<File> files2) {
        List<File> result = new ArrayList<File>();
        result.addAll(files1);
        result.addAll(files2);
        return result;
    }
}
