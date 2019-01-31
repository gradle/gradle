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
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class DefaultSingletonFileTreeTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def hasUsefulDisplayName() {
        File f = new File('test-file')
        DefaultSingletonFileTree tree = new DefaultSingletonFileTree(f)

        expect:
        tree.displayName == "file '$f'"
    }
    
    def visitsFileAsChildOfRoot() {
        FileVisitor visitor = Mock()
        File f = new File('test-file')
        DefaultSingletonFileTree tree = new DefaultSingletonFileTree(f)

        when:
        tree.visit(visitor)

        then:
        1 * visitor.visitFile(!null) >> { FileVisitDetails details ->
            assert details.file == f
            assert details.path == 'test-file'
        }
        0 * visitor._
    }
    
    def "can be converted to a directory tree"() {
        File f = temporaryFolder.file('test-file')
        f.createNewFile()
        DefaultSingletonFileTree singletonFileTree = new DefaultSingletonFileTree(f)
        def tree = new FileTreeAdapter(singletonFileTree)

        when:
        def fileTrees = tree.getAsFileTrees()
        then:
        fileTrees.size() == 1
        def directoryTree = fileTrees[0]
        directoryTree.dir == f.parentFile
        new FileTreeAdapter(directoryTree).files == [f] as Set
    }

    def "convert filtered tree to empty file trees"() {
        File f = temporaryFolder.file('test-file')
        f.createNewFile()
        DefaultSingletonFileTree singletonFileTree = new DefaultSingletonFileTree(f)
        def tree = new FileTreeAdapter(singletonFileTree).filter { it.name == 'different' }

        when:
        def fileTrees = tree.getAsFileTrees()
        then:
        fileTrees.size() == 0
    }
}
