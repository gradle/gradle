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
import org.gradle.api.file.RelativePath
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.api.internal.file.pattern.PatternMatcherFactory
import org.gradle.api.specs.AndSpec
import org.gradle.api.specs.NotSpec
import org.gradle.api.specs.OrSpec
import org.apache.tools.ant.DirectoryScanner

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

    private static final Set<String> globalExcludes = new HashSet<String>()

    private Set includes = [] as LinkedHashSet
    private Set excludes = [] as LinkedHashSet
    def boolean caseSensitive = true

    static {
        globalExcludes.addAll(DirectoryScanner.DEFAULTEXCLUDES as Collection)
    }

    static def setGlobalExcludes(Collection<String> excludes) {
        globalExcludes.clear()
        globalExcludes.addAll(excludes)
    }
    
    def boolean equals(Object o) {
        if (o.is(this)) {
            return true
        }
        if (o == null || o.getClass() != getClass()) {
            return false
        }
        return o.includes.equals(includes) && o.excludes.equals(excludes)
    }

    def int hashCode() {
        return includes.hashCode() ^ excludes.hashCode()
    }

    public PatternSet copyFrom(PatternFilterable sourcePattern) {
        setIncludes(sourcePattern.includes)
        setExcludes(sourcePattern.excludes)
        this
    }

    public PatternSet intersect() {
        return new IntersectionPatternSet(this)
    }
    
    public Spec<RelativePath> getAsSpec() {
        Spec<RelativePath> includeSpec = Specs.satisfyAll()

        if (includes) {
            List<Spec<RelativePath>> matchers = new ArrayList<Spec<RelativePath>>()
            for (String include: includes) {
                matchers.add(PatternMatcherFactory.getPatternMatcher(true, caseSensitive, include))
            }
            includeSpec = new OrSpec<RelativePath>(matchers as Spec[])
        }

        Collection<String> allExcludes = excludes + globalExcludes
        if (!allExcludes) {
            return includeSpec
        }

        List<Spec<RelativePath>> matchers = new ArrayList<Spec<RelativePath>>()
        for (String exclude: allExcludes) {
            matchers.add(PatternMatcherFactory.getPatternMatcher(false, caseSensitive, exclude))
        }
        Spec<RelativePath> excludeSpec = new NotSpec<RelativePath>(new OrSpec<RelativePath>(matchers as Spec[]))

        if (!includes) {
            return excludeSpec
        }

        return new AndSpec<RelativePath>([includeSpec, excludeSpec] as Spec[])
    }

    public Set<String> getIncludes() {
        includes
    }

    public PatternSet setIncludes(Iterable<String> includes) {
        this.includes.clear()
        include(includes)
    }

    public Set<String> getExcludes() {
        excludes
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

    public PatternFilterable exclude(String... excludes) {
        exclude(excludes as List)
    }

    public PatternFilterable exclude(Iterable<String> excludes) {
        GUtil.addToCollection(this.excludes, excludes)
        this
    }

    protected addIncludesAndExcludesToBuilder(node) {
        this.includes.each {String pattern ->
            node.include(name: pattern)
        }
        this.excludes.each {String pattern ->
            node.exclude(name: pattern)
        }
    }

    def addToAntBuilder(node, String childNodeName = null) {
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

    def Spec<RelativePath> getAsSpec() {
        return new AndSpec<RelativePath>([super.getAsSpec(), other.getAsSpec()] as Spec[])
    }

    def addToAntBuilder(Object node, String childNodeName) {
        node.and {
            super.addToAntBuilder(node, null)
            other.addToAntBuilder(node, null)
        }
    }
}
