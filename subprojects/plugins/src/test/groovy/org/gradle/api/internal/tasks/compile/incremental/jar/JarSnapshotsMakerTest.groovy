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
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfo
import spock.lang.Specification
import spock.lang.Subject

class JarSnapshotsMakerTest extends Specification {

    def cache = Mock(LocalJarSnapshots)
    def info = Mock(ClassDependencyInfo)
    def snapshotter = Mock(DefaultJarSnapshotter)
    def finder = Mock(ClasspathJarFinder)

    @Subject maker = new JarSnapshotsMaker(cache, snapshotter, finder)

    def "stores jar snapshots"() {
        def sn1 = Mock(JarSnapshot); def sn2 = Mock(JarSnapshot)
        def jar1 = new JarArchive(new File("jar1.jar"), Mock(FileTree)); def jar2 = new JarArchive(new File("jar2.jar"), Mock(FileTree))

        def classpath = [new File("foo.zip"), new File("someDir"), new File("some.jar")]

        when:
        maker.storeJarSnapshots(classpath)

        then:
        1 * finder.findJarArchives(classpath) >> [jar1, jar2]
        1 * snapshotter.createSnapshot(jar1) >> sn1
        1 * snapshotter.createSnapshot(jar2) >> sn2
        1 * cache.putSnapshots([(jar1.file): sn1, (jar2.file): sn2])
        0 * _
    }
}
