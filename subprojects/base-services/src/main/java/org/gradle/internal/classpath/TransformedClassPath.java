/*
 * Copyright 2022 the original author or authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.specs.Spec;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

public class TransformedClassPath implements ClassPath {
    private final ClassPath originalClassPath;
    private final ImmutableMap<File, File> transforms;

    private TransformedClassPath(ClassPath originalClassPath, Map<File, File> transforms) {
        this.originalClassPath = originalClassPath;
        this.transforms = ImmutableMap.copyOf(transforms);
    }

    @Override
    public boolean isEmpty() {
        return originalClassPath.isEmpty();
    }

    @Override
    public List<URI> getAsURIs() {
        return originalClassPath.getAsURIs();
    }

    @Override
    public List<File> getAsFiles() {
        return originalClassPath.getAsFiles();
    }

    public List<File> getAsTransformedFiles() {
        List<File> originals = new ArrayList<File>(originalClassPath.getAsFiles());
        ListIterator<File> iter = originals.listIterator();
        while (iter.hasNext()) {
            File original = iter.next();
            iter.set(transforms.getOrDefault(original, original));
        }
        return originals;
    }

    @Override
    public List<URL> getAsURLs() {
        return originalClassPath.getAsURLs();
    }

    @Override
    public URL[] getAsURLArray() {
        return originalClassPath.getAsURLArray();
    }

    ClassPath prepend(DefaultClassPath classPath) {
        return new TransformedClassPath(classPath.plus(originalClassPath), transforms);
    }

    private ClassPath plusWithTransforms(TransformedClassPath classPath) {
        ClassPath mergedOriginals = originalClassPath.plus(classPath.originalClassPath);

        // Merge transformations, keeping in mind that class paths are searched left-to-right.
        ImmutableMap.Builder<File, File> mergedTransforms = ImmutableMap.builderWithExpectedSize(transforms.size() + classPath.transforms.size());
        Set<File> nonTransformedFiles = ImmutableSet.copyOf(originalClassPath.getAsFiles());
        for (Map.Entry<File, File> appendedTransform : classPath.transforms.entrySet()) {
            // If we have non-transformed version of the file on this class path, and transformed in the rhs, then the transform is discarded - the original will win.
            if (!nonTransformedFiles.contains(appendedTransform.getKey())) {
                mergedTransforms.put(appendedTransform);
            }
        }
        // All transforms that we have win over transforms of the same files in the rhs.
        mergedTransforms.putAll(transforms);

        return new TransformedClassPath(mergedOriginals, mergedTransforms.buildKeepingLast());
    }

    @Override
    public ClassPath plus(Collection<File> classPath) {
        return new TransformedClassPath(originalClassPath.plus(classPath), transforms);
    }

    @Override
    public ClassPath plus(ClassPath classPath) {
        if (classPath instanceof TransformedClassPath) {
            return plusWithTransforms((TransformedClassPath) classPath);
        }
        return new TransformedClassPath(originalClassPath.plus(classPath), transforms);
    }

    @Override
    public ClassPath removeIf(Spec<? super File> filter) {
        ClassPath filteredClassPath = originalClassPath.removeIf(filter);
        Set<File> remainingOriginals = ImmutableSet.copyOf(filteredClassPath.getAsFiles());
        ImmutableMap.Builder<File, File> remainingTransforms = ImmutableMap.builderWithExpectedSize(Math.min(remainingOriginals.size(), transforms.size()));
        for (Map.Entry<File, File> remainingEntry : transforms.entrySet()) {
            if (remainingOriginals.contains(remainingEntry.getKey())) {
                remainingTransforms.put(remainingEntry);
            }
        }
        return new TransformedClassPath(filteredClassPath, remainingTransforms.build());
    }

    @Nullable
    public File findTransformedJarFor(File originalJar) {
        return transforms.get(originalJar);
    }

    @Override
    public int hashCode() {
        return originalClassPath.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        TransformedClassPath other = (TransformedClassPath) obj;
        return originalClassPath.equals(other.originalClassPath);
    }

    public static class Builder {
        private final DefaultClassPath.Builder originals;
        private final ImmutableMap.Builder<File, File> files;

        private Builder(int expectedSize) {
            originals = DefaultClassPath.builderWithExactSize(expectedSize);
            files = ImmutableMap.builderWithExpectedSize(expectedSize);
        }

        public static Builder withExpectedSize(int expectedSize) {
            return new Builder(expectedSize);
        }

        public Builder add(File original, File transformed) {
            originals.add(original);
            if (!original.equals(transformed)) {
                files.put(original, transformed);
            }
            return this;
        }

        public TransformedClassPath build() {
            Map<File, File> transformedMap = files.build();
            return new TransformedClassPath(originals.build(), transformedMap);
        }
    }
}
