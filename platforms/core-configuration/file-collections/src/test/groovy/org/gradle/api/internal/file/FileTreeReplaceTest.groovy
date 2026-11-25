/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class FileTreeReplaceTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "tree backed by a collection can replace the collection"() {
        def a = tmpDir.file("a.txt").createFile()
        def b = tmpDir.file("b.txt").createFile()
        def c = tmpDir.file("c.txt").createFile()
        def collection = TestFiles.fixed(a, b)
        def tree = collection.asFileTree as FileCollectionInternal

        expect:
        tree.replace(TestFiles.fixed(c), { throw new UnsupportedOperationException() }).is(tree)
        tree.replace(tree, { TestFiles.fixed(c) }).files == [c] as Set
        tree.replace(collection, { TestFiles.fixed(a, c) }).files == [a, c] as Set
    }

    def "filtered tree keeps its patterns when the backing collection is replaced"() {
        def a = tmpDir.file("a.txt").createFile()
        def b = tmpDir.file("b.java").createFile()
        def c = tmpDir.file("c.java").createFile()
        def collection = TestFiles.fixed(a, b)
        def tree = collection.asFileTree.matching { include "*.java" } as FileCollectionInternal

        expect:
        tree.files == [b] as Set
        tree.replace(TestFiles.fixed(c), { throw new UnsupportedOperationException() }).is(tree)
        tree.replace(collection, { TestFiles.fixed(a, b, c) }).files == [b, c] as Set
    }
}
