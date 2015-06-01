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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.apache.tools.ant.DirectoryScanner;
import org.gradle.api.Action;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.RelativePathSpec;
import org.gradle.api.internal.file.pattern.PatternMatcherFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.AntBuilderAware;
import org.gradle.api.tasks.util.internal.PatternSetAntBuilderDelegate;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Standalone implementation of {@link PatternFilterable}.
 */
public class PatternSet implements AntBuilderAware, PatternFilterable {

    private static final NotationParser<Object, String> PARSER = NotationParserBuilder.toType(String.class).fromCharSequence().toComposite();

    private final Set<String> includes = Sets.newLinkedHashSet();
    private final Set<String> excludes = Sets.newLinkedHashSet();
    private final Set<Spec<FileTreeElement>> includeSpecs = Sets.newLinkedHashSet();
    private final Set<Spec<FileTreeElement>> excludeSpecs = Sets.newLinkedHashSet();

    boolean caseSensitive = true;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PatternSet that = (PatternSet) o;

        if (caseSensitive != that.caseSensitive) {
            return false;
        }
        if (excludeSpecs != null ? !excludeSpecs.equals(that.excludeSpecs) : that.excludeSpecs != null) {
            return false;
        }
        if (excludes != null ? !excludes.equals(that.excludes) : that.excludes != null) {
            return false;
        }
        if (includeSpecs != null ? !includeSpecs.equals(that.includeSpecs) : that.includeSpecs != null) {
            return false;
        }
        if (includes != null ? !includes.equals(that.includes) : that.includes != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = includes != null ? includes.hashCode() : 0;
        result = 31 * result + (excludes != null ? excludes.hashCode() : 0);
        result = 31 * result + (includeSpecs != null ? includeSpecs.hashCode() : 0);
        result = 31 * result + (excludeSpecs != null ? excludeSpecs.hashCode() : 0);
        result = 31 * result + (caseSensitive ? 1 : 0);
        return result;
    }

    public PatternSet copyFrom(PatternFilterable sourcePattern) {
        return doCopyFrom((PatternSet) sourcePattern);
    }

    protected PatternSet doCopyFrom(PatternSet from) {
        includes.clear();
        excludes.clear();
        includeSpecs.clear();
        excludeSpecs.clear();
        caseSensitive = from.caseSensitive;

        if (from instanceof IntersectionPatternSet) {
            PatternSet other = ((IntersectionPatternSet) from).other;
            PatternSet otherCopy = new PatternSet().copyFrom(other);
            PatternSet intersectCopy = new IntersectionPatternSet(otherCopy);
            intersectCopy.includes.addAll(from.includes);
            intersectCopy.excludes.addAll(from.excludes);
            intersectCopy.includeSpecs.addAll(from.includeSpecs);
            intersectCopy.excludeSpecs.addAll(from.excludeSpecs);
            includeSpecs.add(intersectCopy.getAsSpec());
        } else {
            includes.addAll(from.includes);
            excludes.addAll(from.excludes);
            includeSpecs.addAll(from.includeSpecs);
            excludeSpecs.addAll(from.excludeSpecs);
        }

        return this;
    }

    public PatternSet intersect() {
        return new IntersectionPatternSet(this);
    }

    private static class IntersectionPatternSet extends PatternSet {

        private final PatternSet other;

        public IntersectionPatternSet(PatternSet other) {
            this.other = other;
        }

        public Spec<FileTreeElement> getAsSpec() {
            return Specs.and(super.getAsSpec(), other.getAsSpec());
        }

        public Object addToAntBuilder(Object node, String childNodeName) {
            return PatternSetAntBuilderDelegate.and(node, new Action<Object>() {
                public void execute(Object andNode) {
                    IntersectionPatternSet.super.addToAntBuilder(andNode, null);
                    other.addToAntBuilder(andNode, null);
                }
            });
        }
    }

    public Spec<FileTreeElement> getAsSpec() {
        return Specs.and(getAsIncludeSpec(), Specs.not(getAsExcludeSpec()));
    }

    public Spec<FileTreeElement> getAsIncludeSpec() {
        List<Spec<FileTreeElement>> matchers = Lists.newArrayList();
        for (String include : includes) {
            Spec<RelativePath> patternMatcher = PatternMatcherFactory.getPatternMatcher(true, caseSensitive, include);
            matchers.add(new RelativePathSpec(patternMatcher));
        }

        matchers.addAll(includeSpecs);
        return Specs.or(matchers);
    }

    public Spec<FileTreeElement> getAsExcludeSpec() {
        Collection<String> allExcludes = Sets.newLinkedHashSet(excludes);
        Collections.addAll(allExcludes, DirectoryScanner.getDefaultExcludes());

        List<Spec<FileTreeElement>> matchers = Lists.newArrayList();
        for (String exclude : allExcludes) {
            Spec<RelativePath> patternMatcher = PatternMatcherFactory.getPatternMatcher(false, caseSensitive, exclude);
            matchers.add(new RelativePathSpec(patternMatcher));
        }

        matchers.addAll(excludeSpecs);
        return Specs.or(false, matchers);
    }

    public Set<String> getIncludes() {
        return includes;
    }

    public Set<Spec<FileTreeElement>> getIncludeSpecs() {
        return includeSpecs;
    }

    public PatternSet setIncludes(Iterable<String> includes) {
        this.includes.clear();
        return include(includes);
    }

    public PatternSet include(String... includes) {
        Collections.addAll(this.includes, includes);
        return this;
    }

    public PatternSet include(Iterable includes) {
        for (Object include : includes) {
            this.includes.add(PARSER.parseNotation(include));
        }
        return this;
    }

    public PatternSet include(Spec<FileTreeElement> spec) {
        includeSpecs.add(spec);
        return this;
    }

    public Set<String> getExcludes() {
        return excludes;
    }

    public Set<Spec<FileTreeElement>> getExcludeSpecs() {
        return excludeSpecs;
    }

    public PatternSet setExcludes(Iterable<String> excludes) {
        this.excludes.clear();
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
        CollectionUtils.addAll(this.includeSpecs, includeSpecs);
        return this;
    }

    public PatternSet include(Closure closure) {
        include(Specs.<FileTreeElement>convertClosureToSpec(closure));
        return this;
    }

    public PatternSet exclude(String... excludes) {
        Collections.addAll(this.excludes, excludes);
        return this;
    }

    public PatternSet exclude(Iterable excludes) {
        for (Object exclude : excludes) {
            this.excludes.add(PARSER.parseNotation(exclude));
        }
        return this;
    }

    public PatternSet exclude(Spec<FileTreeElement> spec) {
        excludeSpecs.add(spec);
        return this;
    }

    public PatternSet excludeSpecs(Iterable<Spec<FileTreeElement>> excludes) {
        CollectionUtils.addAll(this.excludeSpecs, excludes);
        return this;
    }

    public PatternSet exclude(Closure closure) {
        exclude(Specs.<FileTreeElement>convertClosureToSpec(closure));
        return this;
    }

    public Object addToAntBuilder(Object node, String childNodeName) {

        if (!includeSpecs.isEmpty() || !excludeSpecs.isEmpty()) {
            throw new UnsupportedOperationException("Cannot add include/exclude specs to Ant node. Only include/exclude patterns are currently supported.");
        }

        return new PatternSetAntBuilderDelegate(includes, excludes, caseSensitive).addToAntBuilder(node, childNodeName);
    }
}
