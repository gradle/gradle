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
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassFilesAnalyzer
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

@UsesNativeServices
class DefaultJarSnapshotterTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()
    def hasher = Mock(Hasher)

    @Subject snapshotter = new DefaultJarSnapshotter(hasher, Mock(ClassDependenciesAnalyzer))

    def "creates snapshot for an empty jar"() {
        expect:
        def snapshot = snapshotter.createSnapshot(new byte[0], new JarArchive(new File("a.jar"), new FileTreeAdapter(new DirectoryFileTree(new File("missing")))))
        snapshot.hashes.isEmpty()
        snapshot.analysis
    }

    def "creates snapshot of a jar with classes"() {
        def f1 = temp.createFile("foo/Foo.class")
        def f2 = temp.createFile("foo/com/Foo2.class")
        def analyzer = Mock(ClassFilesAnalyzer)

        when:
        def snapshot = snapshotter.createSnapshot(new byte[0], new FileTreeAdapter(new DirectoryFileTree(temp.file("foo"))), analyzer)

        then:
        2 * analyzer.visitFile(_)
        1 * hasher.hash(f1)
        1 * hasher.hash(f2)
        1 * analyzer.getAnalysis() >> Stub(ClassSetAnalysisData)
        0 * _._

        and:
        snapshot.hashes.keySet() == ["Foo", "com.Foo2"] as Set
        snapshot.analysis
    }
}
