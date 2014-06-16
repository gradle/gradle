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
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfo
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfoExtractor
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Subject

@Ignore //TODO SF fixme
class JarSnapshotterTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()
    def hasher = Mock(Hasher)

    @Subject snapshotter = new JarSnapshotter(hasher, Mock(ClassDependenciesAnalyzer), incrementalCompilationCache, project.getGradle())

    def "creates snapshot for an empty jar"() {
        expect:
        def snapshot = snapshotter.createSnapshot(new JarArchive(new File("a.jar"), new FileTreeAdapter(new DirectoryFileTree(new File("missing")))))
        snapshot.hashes.isEmpty()
        snapshot.info
    }

    def "creates snapshot of a jar with classes"() {
        def f1 = temp.createFile("foo/Foo.class")
        def f2 = temp.createFile("foo/com/Foo2.class")
        def extractor = Mock(ClassDependencyInfoExtractor)
        def info = Stub(ClassDependencyInfo)

        when:
        def snapshot = snapshotter.createSnapshot(new FileTreeAdapter(new DirectoryFileTree(temp.file("foo"))), extractor)

        then:
        2 * extractor.visitFile(_)
        1 * hasher.hash(f1)
        1 * hasher.hash(f2)
        1 * extractor.getDependencyInfo() >> info
        0 * _._

        and:
        snapshot.hashes.keySet() == ["Foo", "com.Foo2"] as Set
        snapshot.info == info
    }
}
