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

package org.gradle.api.tasks.util

import org.gradle.api.tasks.AntBuilderAware
import org.gradle.util.GUtil

import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.api.internal.file.pattern.PatternMatcherFactory
import org.gradle.api.specs.AndSpec
import org.gradle.api.specs.NotSpec
import org.gradle.api.specs.OrSpec
import org.apache.tools.ant.DirectoryScanner
import org.gradle.api.file.FileTreeElement
import org.gradle.api.internal.file.RelativePathSpec

/**
 * @author Hans Dockter
 */
class PatternSet implements AntBuilderAware, PatternFilterable {
    PatternSet() {
    }

    PatternSet(Map args) {
        args.each {String key, value ->
            this."$key" = value
        }
    }

    private static final Set<String> GLOBAL_EXCLUDES = new HashSet<String>()

    private Set includes = [] as LinkedHashSet
    private Set excludes = [] as LinkedHashSet
    private Set includeSpecs = [] as LinkedHashSet
    private Set excludeSpecs = [] as LinkedHashSet
    def boolean caseSensitive = true

    static {
        GLOBAL_EXCLUDES.addAll(DirectoryScanner.DEFAULTEXCLUDES as Collection)
    }

    static def setGlobalExcludes(Collection<String> excludes) {
        GLOBAL_EXCLUDES.clear()
        GLOBAL_EXCLUDES.addAll(excludes)
    }
    
    def boolean equals(Object o) {
        if (o.is(this)) {
            return true
        }
        if (o == null || o.getClass() != getClass()) {
            return false
        }
        return o.includes == includes && o.excludes == excludes && o.caseSensitive == caseSensitive
    }

    def int hashCode() {
        return includes.hashCode() ^ excludes.hashCode()
    }

    public PatternSet copyFrom(PatternFilterable sourcePattern) {
        setIncludes(sourcePattern.includes)
        setExcludes(sourcePattern.excludes)
        PatternSet other = sourcePattern
        includeSpecs.clear()
        includeSpecs.addAll(other.includeSpecs)
        excludeSpecs.clear()
        excludeSpecs.addAll(other.excludeSpecs)
        this
    }

    public PatternSet intersect() {
        return new IntersectionPatternSet(this)
    }
    
    public Spec<FileTreeElement> getAsSpec() {
        Spec<FileTreeElement> includeSpec = Specs.satisfyAll()

        boolean hasIncludes = includes || includeSpecs
        if (hasIncludes) {
            List<Spec<FileTreeElement>> matchers = []
            for (String include: includes) {
                matchers.add(new RelativePathSpec(PatternMatcherFactory.getPatternMatcher(true, caseSensitive, include)))
            }
            matchers.addAll(includeSpecs)
            includeSpec = new OrSpec<FileTreeElement>(matchers as Spec[])
        }

        Collection<String> allExcludes = excludes + GLOBAL_EXCLUDES
        boolean hasExcludes = allExcludes || excludeSpecs
        if (!hasExcludes) {
            return includeSpec
        }

        List<Spec<FileTreeElement>> matchers = []
        for (String exclude: allExcludes) {
            matchers.add(new RelativePathSpec(PatternMatcherFactory.getPatternMatcher(false, caseSensitive, exclude)))
        }
        matchers.addAll(excludeSpecs)
        Spec<FileTreeElement> excludeSpec = new NotSpec<FileTreeElement>(new OrSpec<FileTreeElement>(matchers as Spec[]))

        if (!hasIncludes) {
            return excludeSpec
        }

        return new AndSpec<FileTreeElement>([includeSpec, excludeSpec] as Spec[])
    }

    public Set<String> getIncludes() {
        includes
    }

    public Set<Spec<FileTreeElement>> getIncludeSpecs() {
        return includeSpecs
    }

    public PatternSet setIncludes(Iterable<String> includes) {
        this.includes.clear()
        include(includes)
    }

    public Set<String> getExcludes() {
        excludes
    }

    public Set<Spec<FileTreeElement>> getExcludeSpecs() {
       return excludeSpecs
    }

    public PatternSet setExcludes(Iterable<String> excludes) {
        this.excludes.clear()
        exclude(excludes)
    }

    public PatternFilterable include(String... includes) {
        include(includes as List)
    }

    public PatternFilterable include(Iterable<String> includes) {
        GUtil.addToCollection(this.includes, includes)
        this
    }

    public PatternFilterable include(Spec<FileTreeElement> spec) {
        includeSpecs << spec
        this
    }

    /*
    This can't be called just include, because it has the same erasure as include(Iterable<String>)
     */
    public PatternFilterable includeSpecs(Iterable<Spec<FileTreeElement>> includes) {
        GUtil.addToCollection(this.includeSpecs, includes)
        this
    }

    public PatternFilterable include(Closure closure) {
        include(closure as Spec)
        this
    }

    public PatternFilterable exclude(String... excludes) {
        exclude(excludes as List)
    }

    public PatternFilterable exclude(Iterable<String> excludes) {
        GUtil.addToCollection(this.excludes, excludes)
        this
    }

    public PatternFilterable exclude(Spec<FileTreeElement> spec) {
        excludeSpecs << spec
        this
    }

    public PatternFilterable excludeSpecs(Iterable<Spec<FileTreeElement>> excludes) {
        GUtil.addToCollection(this.excludeSpecs, excludes)
        this
    }

    public PatternFilterable exclude(Closure closure) {
        exclude(closure as Spec)
        this
    }

    def addToAntBuilder(node, String childNodeName = null) {
        if (!includeSpecs.empty || !excludeSpecs.empty) {
            throw new UnsupportedOperationException('Cannot add include/exclude specs to Ant node. Only include/exclude patterns are currently supported.')
        }
        node.and {
            if (includes) {
                or {
                    includes.each {
                        filename(name: it, casesensitive: this.caseSensitive)
                    }
                }
            }
            if (excludes) {
                not {
                    or {
                        excludes.each {
                            filename(name: it, casesensitive: this.caseSensitive)
                        }
                    }
                }
            }
        }
    }
}

class IntersectionPatternSet extends PatternSet {
    private final PatternSet other

    def IntersectionPatternSet(PatternSet other) {
        this.other = other
    }

    def Spec<FileTreeElement> getAsSpec() {
        return new AndSpec<FileTreeElement>([super.getAsSpec(), other.getAsSpec()] as Spec[])
    }

    def addToAntBuilder(Object node, String childNodeName) {
        node.and {
            super.addToAntBuilder(node, null)
            other.addToAntBuilder(node, null)
        }
    }
}
