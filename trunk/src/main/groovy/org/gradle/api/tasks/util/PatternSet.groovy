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

/**
 * @author Hans Dockter
 */
class PatternSet implements AntBuilderAware {
    def contextObject

    PatternSet() {
        contextObject = this
    }

    PatternSet(Object contextObject) {
        this.contextObject = contextObject ?: this
    }

    PatternSet(Map args) {
        args.each {String key, value ->
            this."$key" = value
        }
        contextObject = this
    }


    LinkedHashSet includes = []
    LinkedHashSet excludes = []

    def include(String[] includes) {
        this.includes.addAll((includes as LinkedHashSet))
        contextObject
    }

    def exclude(String[] excludes) {
        this.excludes.addAll((excludes as LinkedHashSet))
        contextObject
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
