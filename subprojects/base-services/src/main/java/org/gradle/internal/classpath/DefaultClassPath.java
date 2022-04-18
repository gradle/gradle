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

import org.gradle.api.specs.NotSpec;
import org.gradle.api.specs.Spec;
import org.gradle.internal.UncheckedException;
import org.gradle.util.internal.CollectionUtils;

import java.io.File;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.gradle.internal.Cast.uncheckedNonnullCast;

/**
 * An immutable classpath.
 */
public class DefaultClassPath implements ClassPath, Serializable {

    public static Builder builderWithExactSize(int size) {
        return new Builder(size);
    }

    public static ClassPath of(Iterable<File> files) {
        if (files == null) {
            return EMPTY;
        } else if (files instanceof Collection) {
            return of((Collection<File>) files);
        } else {
            List<File> list = new ArrayList<File>();
            for (File file : files) {
                list.add(file);
            }
            return of(list);
        }
    }

    public static ClassPath of(File... files) {
        if (files == null || files.length == 0) {
            return EMPTY;
        } else {
            return of(Arrays.asList(files));
        }
    }

    /**
     * Only here for the Kotlin DSL, use {@link #of(Iterable)} instead.
     */
    public static ClassPath of(Collection<File> files) {
        if (files == null || files.isEmpty()) {
            return EMPTY;
        } else {
            return new DefaultClassPath(ImmutableUniqueList.of(files));
        }
    }

    private final ImmutableUniqueList<File> files;

    DefaultClassPath() {
        this(ImmutableUniqueList.<File>empty());
    }

    protected DefaultClassPath(ImmutableUniqueList<File> files) {
        this.files = files;
    }

    @Override
    public String toString() {
        return files.toString();
    }

    @Override
    public boolean isEmpty() {
        return files.isEmpty();
    }

    @Override
    public List<URI> getAsURIs() {
        List<URI> urls = new ArrayList<URI>(files.size());
        for (File file : files) {
            urls.add(file.toURI());
        }
        return urls;
    }

    @Override
    public List<File> getAsFiles() {
        return files;
    }

    @Override
    public URL[] getAsURLArray() {
        URL[] urls = new URL[files.size()];
        int i = 0;
        for (File file : files) {
            urls[i++] = toURL(file);
        }
        return urls;
    }

    @Override
    public List<URL> getAsURLs() {
        List<URL> urls = new ArrayList<URL>(files.size());
        for (File file : files) {
            urls.add(toURL(file));
        }
        return urls;
    }

    private static URL toURL(File file) {
        try {
            return file.toURI().toURL();
        } catch (MalformedURLException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public ClassPath plus(ClassPath other) {
        if (isEmpty()) {
            return other;
        }
        if (other.isEmpty()) {
            return this;
        }
        return new DefaultClassPath(concat(files, other.getAsFiles()));
    }

    @Override
    public ClassPath plus(Collection<File> other) {
        if (other.isEmpty()) {
            return this;
        }
        return new DefaultClassPath(concat(files, other));
    }

    @Override
    public ClassPath removeIf(Spec<? super File> filter) {
        List<File> remainingFiles = CollectionUtils.filter(files, new NotSpec<File>(filter));
        if (remainingFiles.size() == files.size()) {
            return this;
        }
        return DefaultClassPath.of(remainingFiles);
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

    private static ImmutableUniqueList<File> concat(Collection<File> files1, Collection<File> files2) {
        ImmutableUniqueList.Builder<File> builder = ImmutableUniqueList.builderWithExpectedSize(files1.size() + files2.size());
        builder.addAll(files1);
        builder.addAll(files2);
        return builder.build();
    }

    public static class Builder {

        private final ImmutableUniqueList.BoundedBuilder<File> uniqueListBuilder;

        public Builder(int exactSize) {
            uniqueListBuilder = ImmutableUniqueList.builderWithExpectedSize(exactSize);
        }

        public void add(File file) {
            uniqueListBuilder.add(file);
        }

        public ClassPath build() {
            return new DefaultClassPath(uniqueListBuilder.buildWithExactSize());
        }
    }

    protected static final class ImmutableUniqueList<T> extends AbstractList<T> implements Serializable {
        private static final ImmutableUniqueList<Object> EMPTY = new ImmutableUniqueList<Object>(Collections.emptySet());

        public static <T> ImmutableUniqueList<T> takeOwnership(HashSet<T> set) {
            return set.isEmpty()
                ? ImmutableUniqueList.<T>empty()
                : new ImmutableUniqueList<T>(set);
        }

        public static <T> ImmutableUniqueList<T> of(Collection<T> collection) {
            if (collection.isEmpty()) {
                return empty();
            }
            Builder<T> builder = builderWithExpectedSize(collection.size());
            builder.addAll(collection);
            return builder.build();
        }

        public static <T> BoundedBuilder<T> builderWithExpectedSize(int size) {
            return new BoundedBuilder<T>(size);
        }

        public static <T> Builder<T> builder() {
            return new UnboundedBuilder<T>();
        }

        public static abstract class Builder<T> {

            public void addAll(Collection<T> coll) {
                for (T e : coll) {
                    add(e);
                }
            }

            public abstract boolean add(T element);

            public abstract ImmutableUniqueList<T> build();
        }

        private static class UnboundedBuilder<T> extends Builder<T> {

            private boolean built = false;
            private final HashSet<T> set = new HashSet<T>();
            private final ArrayList<T> list = new ArrayList<T>();

            @Override
            public boolean add(T element) {
                if (built) {
                    throw new IllegalStateException("Cannot add more elements after the list has been built.");
                }
                if (set.add(element)) {
                    list.add(element);
                    return true;
                }
                return false;
            }

            @Override
            public ImmutableUniqueList<T> build() {
                built = true;
                return new ImmutableUniqueList<T>(set, list.toArray());
            }
        }

        public static class BoundedBuilder<T> extends Builder<T> {

            private final HashSet<T> set;
            private final Object[] array;
            private int inserted;

            public BoundedBuilder(int size) {
                set = new HashSet<T>(size);
                array = new Object[size];
                inserted = 0;
            }

            @Override
            public boolean add(T element) {
                if (set.add(element)) {
                    if (inserted < array.length) {
                        array[inserted] = element;
                        inserted += 1;
                        return true;
                    } else {
                        throw new IllegalStateException("Trying to insert more elements than size given!");
                    }
                }
                return false;
            }

            @Override
            public ImmutableUniqueList<T> build() {
                return new ImmutableUniqueList<T>(set, shrinkArray());
            }

            public ImmutableUniqueList<T> buildWithExactSize() {
                assert array.length == inserted;
                return new ImmutableUniqueList<T>(set, array);
            }

            private Object[] shrinkArray() {
                if (array.length == inserted) {
                    return array;
                }
                Object[] newArray = new Object[inserted];
                System.arraycopy(array, 0, newArray, 0, inserted);
                return newArray;
            }
        }

        @SuppressWarnings("unchecked")
        public static <T> ImmutableUniqueList<T> empty() {
            return (ImmutableUniqueList<T>) EMPTY;
        }

        private final Object[] asArray;
        private final Set<T> asSet;
        private final int size;

        /**
         * Unsafe constructor for internally created Sets that we know won't be mutated.
         */
        ImmutableUniqueList(Set<T> from) {
            this(from, from.toArray(new Object[0]));
        }

        /**
         * Unsafe constructor for {@link Builder}.
         */
        ImmutableUniqueList(Set<T> set, Object[] array) {
            size = array.length;
            asArray = array;
            asSet = set;
        }

        @Override
        public T get(int index) {
            return uncheckedNonnullCast(asArray[index]);
        }

        @Override
        public boolean contains(Object o) {
            return asSet.contains(o);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return asSet.containsAll(c);
        }

        @Override
        public int size() {
            return size;
        }
    }
}
