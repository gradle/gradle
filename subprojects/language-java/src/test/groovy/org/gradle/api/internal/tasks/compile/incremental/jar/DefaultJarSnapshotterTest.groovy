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
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.hash.FileHasher
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer
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
        def snapshot = snapshotter.createSnapshot(HashCode.fromInt(123), new JarArchive(new File("a.jar"), new FileTreeAdapter(new DirectoryFileTree(new File("missing"))), TestFiles.resolver().getPatternSetFactory()))
        snapshot.hashes.isEmpty()
        snapshot.analysis
    }

    def "creates snapshot of a jar with classes"() {
        temp.createFile("foo/Foo.class")
        temp.createFile("foo/com/Foo2.class")
        def jarFile = temp.file("foo")
        def f1Hash = HashCode.fromInt(1)
        def f2Hash = HashCode.fromInt(2)

        when:
        def snapshot = snapshotter.createSnapshot(HashCode.fromInt(123), new JarArchive(jarFile, new FileTreeAdapter(new DirectoryFileTree(jarFile)), TestFiles.resolver().getPatternSetFactory()))

        then:
        1 * hasher.hash(_) >> f1Hash
        1 * classDependenciesAnalyzer.getClassAnalysis("Foo", f2Hash, _) >> Stub(ClassAnalysis)
        1 * hasher.hash(_) >> f2Hash
        1 * classDependenciesAnalyzer.getClassAnalysis("com.Foo2", f1Hash, _) >> Stub(ClassAnalysis)
        0 * _._

        and:
        snapshot.hashes.keySet() == ["Foo", "com.Foo2"] as Set
        snapshot.analysis
    }
}
