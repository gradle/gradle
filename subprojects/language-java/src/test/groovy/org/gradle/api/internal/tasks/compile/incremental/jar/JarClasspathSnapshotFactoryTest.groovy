/*
 * Copyright 2014 the original author or authors.
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
import spock.lang.Specification
import spock.lang.Subject

class JarClasspathSnapshotFactoryTest extends Specification {

    def snapshotter = Mock(JarSnapshotter)
    @Subject factory = new JarClasspathSnapshotFactory(snapshotter)

    def "creates classpath snapshot with correct duplicate classes"() {
        def jar1 = stubArchive("f1"); def jar2 = stubArchive("f2"); def jar3 = stubArchive("f3")

        def sn1 = Stub(JarSnapshot) { getClasses() >> ["A", "B", "C"] }
        def sn2 = Stub(JarSnapshot) { getClasses() >> ["C", "D"] }
        def sn3 = Stub(JarSnapshot) { getClasses() >> ["B", "E"] }

        when:
        def s = factory.createSnapshot([jar1, jar2, jar3])

        then:
        1 * snapshotter.createSnapshot(jar1) >> sn1
        1 * snapshotter.createSnapshot(jar2) >> sn2
        1 * snapshotter.createSnapshot(jar3) >> sn3
        0 * _

        s.data.duplicateClasses == ["B", "C"] as Set
    }

    def "creates classpath snapshot with correct hashes"() {
        def jar1 = stubArchive("f1")
        def jar2 = stubArchive("f2")

        def sn1 = Stub(JarSnapshot) { getHash() >> HashCode.fromString("1234") }
        def sn2 = Stub(JarSnapshot) { getHash() >> HashCode.fromString("2345") }

        when:
        def s = factory.createSnapshot([jar1, jar2])

        then:
        1 * snapshotter.createSnapshot(jar1) >> sn1
        1 * snapshotter.createSnapshot(jar2) >> sn2

        s.data.jarHashes.size() == 2
        s.data.jarHashes[new File("f1")] == HashCode.fromString("1234")
        s.data.jarHashes[new File("f2")] == HashCode.fromString("2345")
    }

    def "doesn't call snapshotter if file doesn't exist"() {
        def jar1 = stubArchive("f1", true)
        def jar2 = stubArchive("f2", false)

        def sn1 = Stub(JarSnapshot) { getHash() >> HashCode.fromString("1234") }

        when:
        factory.createSnapshot([jar1, jar2])

        then:
        1 * snapshotter.createSnapshot(jar1) >> sn1
        0 * snapshotter.createSnapshot(jar2)
    }

    private JarArchive stubArchive(String name, boolean exists = true) {
        new JarArchive(new File(name) {
            boolean exists() { exists }
        }, Stub(FileTree))
    }
}
