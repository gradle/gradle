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

package org.gradle.api.internal.tasks.compile.incremental.classpath

import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.DefaultFileVisitDetails
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis
import org.gradle.internal.hash.FileHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.StreamHasher
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

@UsesNativeServices
class DefaultClasspathEntrySnapshotterTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider(getClass())

    def fileHasher = Mock(FileHasher)
    def streamHasher = Mock(StreamHasher)
    def classDependenciesAnalyzer = Mock(ClassDependenciesAnalyzer)
    def fileOperations = Mock(FileOperations)
    @Subject snapshotter = new DefaultClasspathEntrySnapshotter(fileHasher, streamHasher, classDependenciesAnalyzer, fileOperations)

    def "creates snapshot for an empty entry"() {
        expect:
        def snapshot = snapshotter.createSnapshot(HashCode.fromInt(123), temp.file("foo"))
        snapshot.hashes.isEmpty()
        snapshot.classAnalysis
    }

    def "creates snapshot of an entry with classes"() {
        def f1 = temp.createFile("foo/Foo.class")
        def f2 = temp.createFile("foo/com/Foo2.class")
        def f3 = temp.createFile("foo/com/app.properties")
        def entry = temp.file("foo")
        def f1Hash = HashCode.fromInt(1)
        def f2Hash = HashCode.fromInt(2)
        def f1Details = new DefaultFileVisitDetails(f1, null, null)
        def f2Details = new DefaultFileVisitDetails(f2, null, null)
        def fileTree = Mock(ConfigurableFileTree)

        when:
        def snapshot = snapshotter.createSnapshot(HashCode.fromInt(123), entry)

        then:
        1 * fileOperations.fileTree(entry) >> fileTree

        1 * fileTree.visit(_) >> { FileVisitor visitor ->
            visitor.visitFile(f1Details)
            visitor.visitFile(f2Details)
            visitor.visitFile(new DefaultFileVisitDetails(f3, null, null))
        }
        1 * fileHasher.hash(_, _, _) >> f1Hash
        1 * classDependenciesAnalyzer.getClassAnalysis(f1Hash, f1Details) >> Stub(ClassAnalysis) {
            getClassName() >> "Foo"
        }
        1 * fileHasher.hash(_, _, _) >> f2Hash
        1 * classDependenciesAnalyzer.getClassAnalysis(f2Hash, f2Details) >> Stub(ClassAnalysis) {
            getClassName() >> "com.Foo2"
        }
        0 * _._

        and:
        snapshot.hashes == ["Foo": f1Hash, "com.Foo2": f2Hash]
        snapshot.classAnalysis
    }
}
