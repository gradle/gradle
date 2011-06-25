/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file

import org.gradle.api.tasks.AntBuilderAware
import org.gradle.api.internal.file.collections.DirectoryFileTree

class AntFileCollectionMatchingTaskBuilder implements AntBuilderAware {
    private final Iterable<DirectoryFileTree> fileTrees

    def AntFileCollectionMatchingTaskBuilder(Iterable<DirectoryFileTree> fileTrees) {
        this.fileTrees = fileTrees
    }

    def addToAntBuilder(Object node, String childNodeName) {
        def existing = fileTrees.findAll { it.dir.exists()}
        existing.each {DirectoryFileTree fileTree ->
            node."$childNodeName"(location: fileTree.dir)
        }
        node.or {
            existing.each {DirectoryFileTree fileTree ->
                and {
                    gradleBaseDirSelector(baseDir: fileTree.dir)
                    fileTree.patternSet.addToAntBuilder(node, null)
                }
            }
        }
    }
}
