/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental

import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.hash.Hasher
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class JarSnapshotterTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()
    def hasher = Mock(Hasher)
    @Subject snapshotter = new JarSnapshotter(hasher);

    def "creates snapshot of an empty jar"() {
        expect:
        def snapshot = snapshotter.createSnapshot(new FileTreeAdapter(new DirectoryFileTree(new File("missing"))))
        snapshot.classHashes.isEmpty()
    }

    def "creates snapshot of a jar with classes"() {
        def f1 = temp.createFile("foo/Foo.class")
        def f2 = temp.createFile("foo/com/Foo2.class")

        when:
        def snapshot = snapshotter.createSnapshot(new FileTreeAdapter(new DirectoryFileTree(temp.file("foo"))))

        then:
        snapshot.classHashes.keySet() == ["Foo", "com.Foo2"] as Set
        1 * hasher.hash(f1);
        1 * hasher.hash(f2);
        0 * _._
    }
}
