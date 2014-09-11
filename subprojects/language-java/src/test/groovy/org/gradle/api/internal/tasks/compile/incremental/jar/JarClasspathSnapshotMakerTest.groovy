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

import org.gradle.api.file.FileTree
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysis
import spock.lang.Specification
import spock.lang.Subject

class JarClasspathSnapshotMakerTest extends Specification {

    def store = Mock(LocalJarClasspathSnapshotStore)
    def analysis = Mock(ClassSetAnalysis)
    def factory = Mock(JarClasspathSnapshotFactory)
    def finder = Mock(ClasspathJarFinder)

    @Subject maker = new JarClasspathSnapshotMaker(store, factory, finder)

    def "stores jar snapshots"() {
        def jar1 = new JarArchive(new File("jar1.jar"), Mock(FileTree));
        def jar2 = new JarArchive(new File("jar2.jar"), Mock(FileTree))

        def snapshotData = Stub(JarClasspathSnapshotData)
        def classpathSnapshot = Stub(JarClasspathSnapshot) { getData() >> snapshotData }
        def filesDummy = [new File("f")]

        when:
        maker.storeJarSnapshots(filesDummy)

        then:
        maker.getJarClasspathSnapshot(filesDummy) == classpathSnapshot
        maker.getJarClasspathSnapshot(filesDummy) == classpathSnapshot

        and:
        1 * finder.findJarArchives(filesDummy) >> [jar1, jar2]
        1 * factory.createSnapshot([jar1, jar2]) >> classpathSnapshot
        1 * store.put(snapshotData)
        0 * _
    }

    def "gets classpath snapshot"() {
        def jar1 = new JarArchive(new File("jar1.jar"), Mock(FileTree));

        def classpathSnapshot = Stub(JarClasspathSnapshot)
        def filesDummy = [new File("f")]

        when:
        maker.getJarClasspathSnapshot(filesDummy) == classpathSnapshot
        maker.getJarClasspathSnapshot(filesDummy) == classpathSnapshot

        then:
        1 * finder.findJarArchives(filesDummy) >> [jar1]
        1 * factory.createSnapshot([jar1]) >> classpathSnapshot
        0 * _
    }
}
