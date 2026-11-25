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

package org.gradle.api.tasks.util.internal;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.FilePatterns;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

public final class DefaultFilePatterns implements FilePatterns {

    public static final FilePatterns EMPTY = new DefaultFilePatterns(ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of(), true);

    private final ImmutableSet<String> includes;
    private final ImmutableSet<String> excludes;
    private final ImmutableSet<Spec<FileTreeElement>> includeSpecs;
    private final ImmutableSet<Spec<FileTreeElement>> excludeSpecs;
    private final boolean caseSensitive;

    public DefaultFilePatterns(
        Iterable<String> includes,
        Iterable<String> excludes,
        Iterable<? extends Spec<FileTreeElement>> includeSpecs,
        Iterable<? extends Spec<FileTreeElement>> excludeSpecs,
        boolean caseSensitive
    ) {
        this.includes = strings(includes);
        this.excludes = strings(excludes);
        this.includeSpecs = ImmutableSet.copyOf(includeSpecs);
        this.excludeSpecs = ImmutableSet.copyOf(excludeSpecs);
        this.caseSensitive = caseSensitive;
    }

    // Groovy passes GStrings through Iterable<String>
    private static ImmutableSet<String> strings(Iterable<?> values) {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for (Object value : values) {
            builder.add(value.toString());
        }
        return builder.build();
    }

    @Override
    public Set<String> getIncludes() {
        return includes;
    }

    @Override
    public Set<String> getExcludes() {
        return excludes;
    }

    @Override
    public Set<Spec<FileTreeElement>> getIncludeSpecs() {
        return includeSpecs;
    }

    @Override
    public Set<Spec<FileTreeElement>> getExcludeSpecs() {
        return excludeSpecs;
    }

    @Override
    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    @Override
    public boolean isEmpty() {
        return includes.isEmpty() && excludes.isEmpty() && includeSpecs.isEmpty() && excludeSpecs.isEmpty();
    }

    @Override
    public FilePatterns include(String... includes) {
        return include(Arrays.asList(includes));
    }

    @Override
    public FilePatterns include(Iterable<String> includes) {
        return new DefaultFilePatterns(concat(this.includes, includes), excludes, includeSpecs, excludeSpecs, caseSensitive);
    }

    @Override
    public FilePatterns include(Spec<FileTreeElement> includeSpec) {
        return new DefaultFilePatterns(includes, excludes, ImmutableSet.<Spec<FileTreeElement>>builder().addAll(includeSpecs).add(includeSpec).build(), excludeSpecs, caseSensitive);
    }

    @Override
    public FilePatterns exclude(String... excludes) {
        return exclude(Arrays.asList(excludes));
    }

    @Override
    public FilePatterns exclude(Iterable<String> excludes) {
        return new DefaultFilePatterns(includes, concat(this.excludes, excludes), includeSpecs, excludeSpecs, caseSensitive);
    }

    @Override
    public FilePatterns exclude(Spec<FileTreeElement> excludeSpec) {
        return new DefaultFilePatterns(includes, excludes, includeSpecs, ImmutableSet.<Spec<FileTreeElement>>builder().addAll(excludeSpecs).add(excludeSpec).build(), caseSensitive);
    }

    @Override
    public FilePatterns withIncludes(Iterable<String> includes) {
        return new DefaultFilePatterns(includes, excludes, includeSpecs, excludeSpecs, caseSensitive);
    }

    @Override
    public FilePatterns withExcludes(Iterable<String> excludes) {
        return new DefaultFilePatterns(includes, excludes, includeSpecs, excludeSpecs, caseSensitive);
    }

    @Override
    public FilePatterns withIncludeSpecs(Iterable<? extends Spec<FileTreeElement>> includeSpecs) {
        return new DefaultFilePatterns(includes, excludes, includeSpecs, excludeSpecs, caseSensitive);
    }

    @Override
    public FilePatterns withExcludeSpecs(Iterable<? extends Spec<FileTreeElement>> excludeSpecs) {
        return new DefaultFilePatterns(includes, excludes, includeSpecs, excludeSpecs, caseSensitive);
    }

    @Override
    public FilePatterns caseSensitive(boolean caseSensitive) {
        return caseSensitive == this.caseSensitive ? this : new DefaultFilePatterns(includes, excludes, includeSpecs, excludeSpecs, caseSensitive);
    }

    private static ImmutableSet<String> concat(ImmutableSet<String> first, Iterable<?> second) {
        ImmutableSet.Builder<String> builder = ImmutableSet.<String>builder().addAll(first);
        for (Object value : second) {
            builder.add(value.toString());
        }
        return builder.build();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultFilePatterns)) {
            return false;
        }
        DefaultFilePatterns that = (DefaultFilePatterns) o;
        return caseSensitive == that.caseSensitive
            && includes.equals(that.includes)
            && excludes.equals(that.excludes)
            && includeSpecs.equals(that.includeSpecs)
            && excludeSpecs.equals(that.excludeSpecs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(includes, excludes, includeSpecs, excludeSpecs, caseSensitive);
    }

    @Override
    public String toString() {
        return "FilePatterns{includes=" + includes + ", excludes=" + excludes
            + ", includeSpecs=" + includeSpecs + ", excludeSpecs=" + excludeSpecs
            + ", caseSensitive=" + caseSensitive + "}";
    }
}
