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

package org.gradle.api.tasks.util

import org.gradle.api.Action
import org.gradle.api.tasks.AntBuilderAware

class PatternSetGroovyAntBuilderDelegate implements AntBuilderAware {

    private final Set<String> includes
    private final Set<String> excludes
    private final boolean caseSensitive

    PatternSetGroovyAntBuilderDelegate(Set<String> includes, Set<String> excludes, boolean caseSensitive) {
        this.includes = new HashSet(includes)
        this.excludes = new HashSet(excludes)
        this.caseSensitive = caseSensitive
    }

    static and(Object node, Action<Object> withAndNode) {
        node.and {
            withAndNode.execute(delegate)
        }
    }

    def addToAntBuilder(Object node, String childNodeName) {
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
