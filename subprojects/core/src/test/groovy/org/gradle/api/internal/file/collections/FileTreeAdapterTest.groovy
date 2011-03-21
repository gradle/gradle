/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.file.collections

import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.DefaultConfigurableFileTree
import spock.lang.Specification

class FileTreeAdapterTest extends Specification {
    def toStringUsesDisplayName() {
        MinimalFileTree tree = Mock()
        _ * tree.displayName >> 'display name'

        FileTreeAdapter adapter = new FileTreeAdapter(tree)

        expect:
        adapter.toString() == 'display name'
    }

    def delegatesToTreeToVisitElements() {
        MinimalFileTree tree = Mock()
        FileTreeAdapter adapter = new FileTreeAdapter(tree)
        FileVisitor visitor = Mock()

        when:
        adapter.visit(visitor)

        then:
        1 * tree.visit(visitor)
        0 * _._
    }

    def resolveAddsTargetTreeToContext() {
        MinimalFileTree tree = Mock()
        FileTreeAdapter adapter = new FileTreeAdapter(tree)
        FileCollectionResolveContext context = Mock()

        when:
        adapter.resolve(context)

        then:
        1 * context.add(tree)
        0 * _._
    }

    def delegatesToMirroringTreeToBuildFileSystemMirror() {
        FileSystemMirroringFileTree tree = Mock()
        FileTreeAdapter adapter = new FileTreeAdapter(tree)
        DefaultConfigurableFileTree mirror = new DefaultConfigurableFileTree((Object) null, null, null)

        when:
        def result = adapter.asFileTrees

        then:
        result == [mirror]
        1 * tree.visit(!null) >> { it[0].visitFile({} as FileVisitDetails) }
        1 * tree.mirror >> mirror
        0 * _._
    }
    
    def delegatesToEmptyMirroringTreeToBuildFileSystemMirror() {
        FileSystemMirroringFileTree tree = Mock()
        FileTreeAdapter adapter = new FileTreeAdapter(tree)

        when:
        def result = adapter.asFileTrees

        then:
        result == []
        1 * tree.visit(!null)
        0 * _._
    }
}
