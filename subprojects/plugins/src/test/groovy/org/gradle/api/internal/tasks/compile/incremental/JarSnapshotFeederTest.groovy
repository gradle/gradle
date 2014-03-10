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

import org.gradle.api.file.FileTree
import spock.lang.Specification
import spock.lang.Subject

class JarSnapshotFeederTest extends Specification {

    def cache = Mock(JarSnapshotCache)
    def snapshotter = Mock(JarSnapshotter)

    @Subject feeder = new JarSnapshotFeeder(cache, snapshotter)

    def "stores jar snapshot"() {
        def jar1 = new JarArchive(new File("jar1.jar"), Mock(FileTree))
        def snapshot = Mock(JarSnapshot)

        when:
        feeder.storeJarSnapshots([jar1])

        then:
        1 * cache.getSnapshot(jar1.file)
        1 * snapshotter.createSnapshot(jar1.contents) >> snapshot
        1 * cache.putSnapshots([(jar1.file): snapshot])
        0 * _
    }

    def "stores multiple snapshots"() {
        def jar1 = new JarArchive(new File("jar1.jar"), Mock(FileTree))
        def jar2 = new JarArchive(new File("jar2.jar"), Mock(FileTree))

        when:
        feeder.storeJarSnapshots([jar1, jar2])

        then:
        1 * snapshotter.createSnapshot(jar1.contents) >> Mock(JarSnapshot)
        1 * snapshotter.createSnapshot(jar2.contents) >> Mock(JarSnapshot)
        1 * cache.putSnapshots({ it.size() == 2})
    }

    def "avoids storing unchanged jar snapshots"() {
        def jar1 = new JarArchive(new File("jar1.jar"), Mock(FileTree))
        def jar2 = new JarArchive(new File("jar2.jar"), Mock(FileTree))

        when:
        feeder.changedJar(jar2.file)
        feeder.storeJarSnapshots([jar1, jar2])

        then:
        1 * cache.getSnapshot(jar1.file) >> Mock(JarSnapshot)
        1 * cache.getSnapshot(jar2.file) >> Mock(JarSnapshot)
        1 * snapshotter.createSnapshot(jar2.contents) >> Mock(JarSnapshot)
        1 * cache.putSnapshots({ it[jar2.file] })
        0 * _
    }
}
