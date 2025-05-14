/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.util;

import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.gradle.api.Incubating;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.AntBuilderAware;
import org.gradle.api.tasks.util.internal.IntersectionPatternSet;
import org.gradle.api.tasks.util.internal.PatternSetAntBuilderDelegate;
import org.gradle.api.tasks.util.internal.PatternSpecFactory;
import org.gradle.internal.instrumentation.api.annotations.NotToBeMigratedToLazy;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.util.internal.CollectionUtils;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Standalone implementation of {@link PatternFilterable}.
 */
@NotToBeMigratedToLazy
public class PatternSet implements AntBuilderAware, PatternFilterable {

    private static final NotationParser<Object, String> PARSER = NotationParserBuilder.toType(String.class).fromCharSequence().toComposite();
    private final PatternSpecFactory patternSpecFactory;

    private Set<String> includes;
    private Set<String> excludes;
    private Set<Spec<FileTreeElement>> includeSpecs;
    private Set<Spec<FileTreeElement>> excludeSpecs;
    private boolean caseSensitive = true;

    public PatternSet() {
        this(PatternSpecFactory.INSTANCE);
    }

    protected PatternSet(PatternSet patternSet) {
        this(patternSet.patternSpecFactory);
    }

    protected PatternSet(PatternSpecFactory patternSpecFactory) {
        this.patternSpecFactory = patternSpecFactory;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PatternSet)) {
            return false;
        }

        PatternSet that = (PatternSet) o;

        if (caseSensitive != that.caseSensitive) {
            return false;
        }
        if (!nullToEmpty(excludeSpecs).equals(nullToEmpty(that.excludeSpecs))) {
            return false;
        }
        if (!nullToEmpty(excludes).equals(nullToEmpty(that.excludes))) {
            return false;
        }
        if (!nullToEmpty(includeSpecs).equals(nullToEmpty(that.includeSpecs))) {
            return false;
        }
        if (!nullToEmpty(includes).equals(nullToEmpty(that.includes))) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = nullToEmpty(includes).hashCode();
        result = 31 * result + nullToEmpty(excludes).hashCode();
        result = 31 * result + nullToEmpty(includeSpecs).hashCode();
        result = 31 * result + nullToEmpty(excludeSpecs).hashCode();
        result = 31 * result + (caseSensitive ? 1 : 0);
        return result;
    }

    private static <T> Set<T> nullToEmptyAndUnmodifiableSet(@Nullable Set<T> set) {
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }

    private static Set<?> nullToEmpty(@Nullable Set<?> set) {
        return set == null ? Collections.emptySet() : set;
    }

    public PatternSet copyFrom(PatternFilterable sourcePattern) {
        return doCopyFrom((PatternSet) sourcePattern);
    }

    protected PatternSet doCopyFrom(PatternSet from) {
        caseSensitive = from.caseSensitive;

        if (from instanceof IntersectionPatternSet) {
            PatternSet other = ((IntersectionPatternSet) from).getOther();
            PatternSet otherCopy = new PatternSet(other).copyFrom(other);
            PatternSet intersectCopy = new IntersectionPatternSet(otherCopy);
            copyIncludesAndExcludes(intersectCopy, from);

            includes = null;
            excludes = null;
            includeSpecs = new LinkedHashSet<>();
            includeSpecs.add(intersectCopy.getAsSpec());
            excludeSpecs = null;
        } else {
            copyIncludesAndExcludes(this, from);
        }

        return this;
    }

    private void copyIncludesAndExcludes(PatternSet target, PatternSet from) {
        target.includes = from.includes == null ? null : Sets.newLinkedHashSet(from.includes);
        target.excludes = from.excludes == null ? null : Sets.newLinkedHashSet(from.excludes);
        target.includeSpecs = from.includeSpecs == null ? null : Sets.newLinkedHashSet(from.includeSpecs);
        target.excludeSpecs = from.excludeSpecs == null ? null : Sets.newLinkedHashSet(from.excludeSpecs);
    }

    public PatternSet intersect() {
        if (isEmpty()) {
            return new PatternSet(this.patternSpecFactory);
        } else {
            return new IntersectionPatternSet(this);
        }
    }

    /**
     * The PatternSet is considered empty when no includes or excludes have been added.
     *
     * The Spec returned by getAsSpec method only contains the default excludes patterns
     * in this case.
     *
     * @return true when no includes or excludes have been added to this instance
     */
    public boolean isEmpty() {
        return (includes == null || includes.isEmpty())
            && (excludes == null || excludes.isEmpty())
            && (includeSpecs == null || includeSpecs.isEmpty())
            && (excludeSpecs == null || excludeSpecs.isEmpty());
    }

    public Spec<FileTreeElement> getAsSpec() {
        return patternSpecFactory.createSpec(this);
    }

    public Spec<FileTreeElement> getAsIncludeSpec() {
        return patternSpecFactory.createIncludeSpec(this);
    }

    public Spec<FileTreeElement> getAsExcludeSpec() {
        return patternSpecFactory.createExcludeSpec(this);
    }

    /**
     * Like {@link #getIncludes()}, but returns a unmodifiable view or empty set.
     *
     * <p>Use this if you are only reading from the set, as it avoids allocating a set if not needed.</p>
     *
     * @return the include patterns, or an empty set if none
     * @since 8.8
     */
    @Incubating
    public Set<String> getIncludesView() {
        return nullToEmptyAndUnmodifiableSet(includes);
    }

    @Override
    public Set<String> getIncludes() {
        if (includes == null) {
            includes = new LinkedHashSet<>();
        }
        return includes;
    }

    /**
     * Like {@link #getIncludeSpecs()}, but returns a unmodifiable view or empty set.
     *
     * <p>Use this if you are only reading from the set, as it avoids allocating a set if not needed.</p>
     *
     * @return the include specs, or an empty set if none
     * @since 8.8
     */
    @Incubating
    public Set<Spec<FileTreeElement>> getIncludeSpecsView() {
        return nullToEmptyAndUnmodifiableSet(includeSpecs);
    }

    public Set<Spec<FileTreeElement>> getIncludeSpecs() {
        if (includeSpecs == null) {
            includeSpecs = new LinkedHashSet<>();
        }
        return includeSpecs;
    }

    @Override
    public PatternSet setIncludes(Iterable<String> includes) {
        this.includes = null;
        return include(includes);
    }

    @Override
    public PatternSet include(String... includes) {
        Collections.addAll(getIncludes(), includes);
        return this;
    }

    @Override
    public PatternSet include(Iterable includes) {
        for (Object include : includes) {
            getIncludes().add(PARSER.parseNotation(include));
        }
        return this;
    }

    @Override
    public PatternSet include(Spec<FileTreeElement> spec) {
        getIncludeSpecs().add(spec);
        return this;
    }

    /**
     * Like {@link #getExcludes()}, but returns a unmodifiable view or empty set.
     *
     * <p>Use this if you are only reading from the set, as it avoids allocating a set if not needed.</p>
     *
     * @return the exclude patterns, or an empty set if none
     * @since 8.8
     */
    @Incubating
    public Set<String> getExcludesView() {
        return nullToEmptyAndUnmodifiableSet(excludes);
    }

    @Override
    public Set<String> getExcludes() {
        if (excludes == null) {
            excludes = new LinkedHashSet<>();
        }
        return excludes;
    }

    /**
     * Like {@link #getExcludeSpecs()}, but returns a unmodifiable view or empty set.
     *
     * <p>Use this if you are only reading from the set, as it avoids allocating a set if not needed.</p>
     *
     * @return the exclude specs, or an empty set if none
     * @since 8.8
     */
    @Incubating
    public Set<Spec<FileTreeElement>> getExcludeSpecsView() {
        return nullToEmptyAndUnmodifiableSet(excludeSpecs);
    }

    public Set<Spec<FileTreeElement>> getExcludeSpecs() {
        if (excludeSpecs == null) {
            excludeSpecs = new LinkedHashSet<>();
        }
        return excludeSpecs;
    }

    @Override
    public PatternSet setExcludes(Iterable<String> excludes) {
        this.excludes = null;
        return exclude(excludes);
    }


    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    /*
    This can't be called just include, because it has the same erasure as include(Iterable<String>).
     */
    public PatternSet includeSpecs(Iterable<Spec<FileTreeElement>> includeSpecs) {
        CollectionUtils.addAll(getIncludeSpecs(), includeSpecs);
        return this;
    }

    @Override
    public PatternSet include(Closure closure) {
        include(Specs.<FileTreeElement>convertClosureToSpec(closure));
        return this;
    }

    @Override
    public PatternSet exclude(String... excludes) {
        Collections.addAll(getExcludes(), excludes);
        return this;
    }

    @Override
    public PatternSet exclude(Iterable excludes) {
        for (Object exclude : excludes) {
            getExcludes().add(PARSER.parseNotation(exclude));
        }
        return this;
    }

    @Override
    public PatternSet exclude(Spec<FileTreeElement> spec) {
        getExcludeSpecs().add(spec);
        return this;
    }

    public PatternSet excludeSpecs(Iterable<Spec<FileTreeElement>> excludes) {
        CollectionUtils.addAll(getExcludeSpecs(), excludes);
        return this;
    }

    @Override
    public PatternSet exclude(Closure closure) {
        exclude(Specs.<FileTreeElement>convertClosureToSpec(closure));
        return this;
    }

    @Override
    public Object addToAntBuilder(Object node, String childNodeName) {

        if (!nullToEmpty(includeSpecs).isEmpty() || !nullToEmpty(excludeSpecs).isEmpty()) {
            throw new UnsupportedOperationException("Cannot add include/exclude specs to Ant node. Only include/exclude patterns are currently supported.");
        }

        return new PatternSetAntBuilderDelegate(getIncludesView(), getExcludesView(), isCaseSensitive()).addToAntBuilder(node, childNodeName);
    }
}
