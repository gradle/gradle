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



package org.gradle.api.internal.tasks.compile.incremental.jar

import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.hash.Hasher
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class JarSnapshotterTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()
    @Subject snapshotter = new JarSnapshotter(Mock(Hasher), Mock(ClassDependenciesAnalyzer));

    def "creates snapshot for an empty jar"() {
        expect:
        def snapshot = snapshotter.createSnapshot(new FileTreeAdapter(new DirectoryFileTree(new File("missing"))))
        snapshot.allClasses.isEmpty()
    }

    def "creates snapshot of a jar with classes"() {
        def f1 = temp.createFile("foo/Foo.class")
        def f2 = temp.createFile("foo/com/Foo2.class")
        def sn1 = Mock(ClassSnapshot); def sn2 = Mock(ClassSnapshot)

        when:
        def snapshot = snapshotter.createSnapshot(new FileTreeAdapter(new DirectoryFileTree(temp.file("foo"))), info)

        then:
        1 * classSnapshotter.createSnapshot("Foo", f1, info) >> sn1
        1 * classSnapshotter.createSnapshot("com.Foo2", f2, info) >> sn2

        snapshot.classSnapshots["Foo"] == sn1
        snapshot.classSnapshots["com.Foo2"] == sn2

        0 * _._
    }
}
