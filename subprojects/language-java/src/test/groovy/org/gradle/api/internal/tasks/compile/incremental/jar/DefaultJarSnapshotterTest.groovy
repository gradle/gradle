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

import com.google.common.hash.HashCode
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.DefaultFileVisitDetails
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.hash.FileHasher
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

@UsesNativeServices
class DefaultJarSnapshotterTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()

    def hasher = Mock(FileHasher)
    def classDependenciesAnalyzer = Mock(ClassDependenciesAnalyzer)
    @Subject snapshotter = new DefaultJarSnapshotter(hasher, classDependenciesAnalyzer)

    def "creates snapshot for an empty jar"() {
        expect:
        def snapshot = snapshotter.createSnapshot(HashCode.fromInt(123), new JarArchive(new File("a.jar"), new FileTreeAdapter(new DefaultDirectoryFileTreeFactory().create(new File("missing")))))
        snapshot.hashes.isEmpty()
        snapshot.analysis
    }

    def "creates snapshot of a jar with classes"() {
        def f1 = temp.createFile("foo/Foo.class")
        def f2 = temp.createFile("foo/com/Foo2.class")
        def f3 = temp.createFile("foo/com/app.properties")
        def jarFile = temp.file("foo")
        def f1Hash = HashCode.fromInt(1)
        def f2Hash = HashCode.fromInt(2)
        def f1Details = new DefaultFileVisitDetails(f1, null, null)
        def f2Details = new DefaultFileVisitDetails(f2, null, null)

        def jarFileTree = Mock(FileTree)

        when:
        def snapshot = snapshotter.createSnapshot(HashCode.fromInt(123), new JarArchive(jarFile, jarFileTree))

        then:
        1 * jarFileTree.visit(_) >> { FileVisitor visitor ->
            visitor.visitFile(f1Details)
            visitor.visitFile(f2Details)
            visitor.visitFile(new DefaultFileVisitDetails(f3, null, null))
        }
        1 * hasher.hash(_) >> f1Hash
        1 * classDependenciesAnalyzer.getClassAnalysis(f1Hash, f1Details) >> Stub(ClassAnalysis) {
            getClassName() >> "Foo"
        }
        1 * hasher.hash(_) >> f2Hash
        1 * classDependenciesAnalyzer.getClassAnalysis(f2Hash, f2Details) >> Stub(ClassAnalysis) {
            getClassName() >> "com.Foo2"
        }
        0 * _._

        and:
        snapshot.hashes == ["Foo": f1Hash, "com.Foo2": f2Hash]
        snapshot.analysis
    }
}
