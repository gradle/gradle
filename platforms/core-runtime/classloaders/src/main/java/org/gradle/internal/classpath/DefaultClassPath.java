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

import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.gradle.internal.Cast.uncheckedCast;

/**
 * An immutable classpath.
 */
@NullMarked
public class DefaultClassPath implements ClassPath, Serializable {

    public static Builder builderWithExactSize(int size) {
        return new Builder(size);
    }

    public static ClassPath of(@Nullable Iterable<File> files) {
        if (files == null) {
            return EMPTY;
        } else if (files instanceof Collection) {
            return of((Collection<File>) files);
        } else {
            List<File> list = new ArrayList<>();
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
            return new DefaultClassPath(ImmutableUniqueList.of(Arrays.asList(files)));
        }
    }

    /**
     * Only here for the Kotlin DSL, use {@link #of(Iterable)} instead.
     */
    public static ClassPath of(@Nullable Collection<File> files) {
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
        return uncheckedCast(Arrays.asList(files.asArray.clone()));
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
        if (other instanceof TransformedClassPath) {
            // Any combination of TransformedClassPath and other ClassPath has to remain transformed.
            return ((TransformedClassPath) other).prepend(this);
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
    public ClassPath removeIf(final Spec<? super File> filter) {
        List<File> remainingFiles = files.stream()
            .filter(element -> !filter.isSatisfiedBy(element))
            .collect(Collectors.toList());
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

    private static ImmutableUniqueList<File> concat(ImmutableUniqueList<File> files1, Collection<File> files2) {
        ImmutableUniqueList.Builder<File> builder = ImmutableUniqueList.builderWithExactSize(files1.size() + files2.size());
        for (File file : files1) {
            builder.add(file);
        }
        for (File file : files2) {
            builder.add(file);
        }
        return builder.build();
    }

    public static class Builder {

        private final ImmutableUniqueList.Builder<File> uniqueListBuilder;

        public Builder(int exactSize) {
            uniqueListBuilder = ImmutableUniqueList.builderWithExactSize(exactSize);
        }

        public void add(File file) {
            uniqueListBuilder.add(file);
        }

        public ClassPath build() {
            return new DefaultClassPath(uniqueListBuilder.buildWithExactSize());
        }
    }

    public static final class ImmutableUniqueList<T> implements Iterable<T>, Serializable {
        private static final ImmutableUniqueList<Object> EMPTY = new ImmutableUniqueList<Object>(new Object[0]);

        public static <T> ImmutableUniqueList<T> of(Collection<T> collection) {
            if (collection.isEmpty()) {
                return empty();
            }
            Builder<T> builder = new Builder<T>(collection.size());
            for (T element : collection) {
                builder.add(element);
            }
            return builder.build();
        }

        public static <T> Builder<T> builderWithExactSize(int exactSize) {
            return new Builder<T>(exactSize);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(asArray);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof ImmutableUniqueList)) {
                return false;
            }
            return Arrays.equals(asArray, Cast.<ImmutableUniqueList<T>>uncheckedCast(obj).asArray);
        }

        @Override
        public String toString() {
            return Arrays.toString(asArray);
        }

        public boolean isEmpty() {
            return size() == 0;
        }

        public Stream<T> stream() {
            return StreamSupport.stream(spliterator(), false);
        }

        @Override
        public Spliterator<T> spliterator() {
            return uncheckedCast(Arrays.spliterator(asArray));
        }

        @Override
        public Iterator<T> iterator() {
            return uncheckedCast(Arrays.asList(asArray).iterator());
        }

        public static class Builder<T> {

            private final HashSet<T> set;
            private final Object[] array;
            private int inserted;

            public Builder(int size) {
                set = new HashSet<T>(size);
                array = new Object[size];
                inserted = 0;
            }

            public void add(T element) {
                if (set.add(element)) {
                    if (inserted < array.length) {
                        array[inserted] = element;
                        inserted += 1;
                    } else {
                        throw new IllegalStateException("Trying to insert more elements than size given!");
                    }
                }
            }

            public ImmutableUniqueList<T> build() {
                return new ImmutableUniqueList<T>(shrinkArray());
            }

            public ImmutableUniqueList<T> buildWithExactSize() {
                assert array.length == inserted;
                return new ImmutableUniqueList<T>(array);
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

        /**
         * Unsafe constructor for {@link Builder}.
         */
        ImmutableUniqueList(Object[] array) {
            asArray = array;
        }

        public T get(int index) {
            if (index >= size()) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
            }
            return Cast.uncheckedNonnullCast(asArray[index]);
        }

        public int size() {
            return asArray.length;
        }
    }
}
