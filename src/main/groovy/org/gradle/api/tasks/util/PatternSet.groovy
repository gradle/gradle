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

    private Set includes = [] as LinkedHashSet
    private Set excludes = [] as LinkedHashSet

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
        node."${childNodeName ?: 'patternset'}"() {
            addIncludesAndExcludesToBuilder(delegate)
        }
    }
}
