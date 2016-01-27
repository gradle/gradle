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

package org.gradle.internal.classpath;

import org.gradle.internal.UncheckedException;

import java.io.File;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;

/**
 * An immutable classpath.
 */
public class DefaultClassPath implements ClassPath, Serializable {
    private final List<File> files;

    DefaultClassPath() {
        this.files = Collections.emptyList();
    }

    public static ClassPath of(Collection<File> files) {
        if (files == null || files.isEmpty()) {
            return EMPTY;
        } else {
            return new DefaultClassPath(files);
        }
    }

    public DefaultClassPath(Iterable<File> files) {
        Set<File> noDuplicates = new LinkedHashSet<File>();
        for (File file : files) {
            noDuplicates.add(file);
        }
        this.files = new ArrayList<File>(noDuplicates);
    }

    private DefaultClassPath(Set<File> files) {
        this.files = new ArrayList<File>(files);
    }

    public DefaultClassPath(File... files) {
        Set<File> noDuplicates = new LinkedHashSet<File>();
        Collections.addAll(noDuplicates, files);
        this.files = new ArrayList<File>(noDuplicates);
    }

    @Override
    public String toString() {
        return files.toString();
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }

    public List<URI> getAsURIs() {
        List<URI> urls = new ArrayList<URI>();
        for (File file : files) {
            urls.add(file.toURI());
        }
        return urls;
    }

    public List<File> getAsFiles() {
        return files;
    }

    public URL[] getAsURLArray() {
        Collection<URL> result = getAsURLs();
        return result.toArray(new URL[0]);
    }

    public List<URL> getAsURLs() {
        List<URL> urls = new ArrayList<URL>();
        for (File file : files) {
            try {
                urls.add(file.toURI().toURL());
            } catch (MalformedURLException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
        return urls;
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

    private Set<File> concat(Collection<File> files1, Collection<File> files2) {
        Set<File> result = new LinkedHashSet<File>();
        result.addAll(files1);
        result.addAll(files2);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        DefaultClassPath other = (DefaultClassPath) obj;
        return files.equals(other.files);
    }

    @Override
    public int hashCode() {
        return files.hashCode();
    }
}
