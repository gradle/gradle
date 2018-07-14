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

import org.gradle.api.file.FileTree
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysis
import spock.lang.Specification
import spock.lang.Subject

class ClasspathSnapshotMakerTest extends Specification {

    def store = Mock(LocalClasspathSnapshotStore)
    def analysis = Mock(ClassSetAnalysis)
    def factory = Mock(ClasspathSnapshotFactory)
    def finder = Mock(ClasspathEntryConverter)

    @Subject maker = new ClasspathSnapshotMaker(store, factory, finder)

    def "stores jar snapshots"() {
        def jar1 = new ClasspathEntry(new File("jar1.jar"), Mock(FileTree));
        def jar2 = new ClasspathEntry(new File("jar2.jar"), Mock(FileTree))

        def snapshotData = Stub(ClasspathSnapshotData)
        def classpathSnapshot = Stub(ClasspathSnapshot) { getData() >> snapshotData }
        def filesDummy = [new File("f")]

        when:
        maker.storeSnapshots(filesDummy)

        then:
        maker.getClasspathSnapshot(filesDummy) == classpathSnapshot
        maker.getClasspathSnapshot(filesDummy) == classpathSnapshot

        and:
        1 * finder.asClasspathEntries(filesDummy) >> [jar1, jar2]
        1 * factory.createSnapshot([jar1, jar2]) >> classpathSnapshot
        1 * store.put(snapshotData)
        0 * _
    }

    def "gets classpath snapshot"() {
        def jar1 = new ClasspathEntry(new File("jar1.jar"), Mock(FileTree));

        def classpathSnapshot = Stub(ClasspathSnapshot)
        def filesDummy = [new File("f")]

        when:
        maker.getClasspathSnapshot(filesDummy) == classpathSnapshot
        maker.getClasspathSnapshot(filesDummy) == classpathSnapshot

        then:
        1 * finder.asClasspathEntries(filesDummy) >> [jar1]
        1 * factory.createSnapshot([jar1]) >> classpathSnapshot
        0 * _
    }
}
