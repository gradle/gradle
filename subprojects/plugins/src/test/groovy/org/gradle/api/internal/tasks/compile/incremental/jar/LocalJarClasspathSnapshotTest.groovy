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

import org.gradle.api.internal.tasks.compile.incremental.cache.JarSnapshotCache
import org.gradle.api.internal.tasks.compile.incremental.cache.LocalJarClasspathSnapshotStore
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class LocalJarClasspathSnapshotTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()

    def hashesStore = Mock(LocalJarClasspathSnapshotStore)
    def snapshotCache = Mock(JarSnapshotCache)

    @Subject cache = new LocalJarClasspathSnapshot(hashesStore, snapshotCache)

    def "empty cache"() {
        when:
        def out = cache.getSnapshot(new File("f"))

        then:
        out == null
        hashesStore.get() >> [:]
        snapshotCache.getJarSnapshots([:]) >> [:]
    }

    def "loads snapshots once"() {
        def snapshot = Mock(JarSnapshot)

        when:
        cache.getSnapshot(new File("f"))
        def out = cache.getSnapshot(new File("f"))

        then:
        out == snapshot
        1 * hashesStore.get() >> [(new File("f")): new byte[0]]
        1 * snapshotCache.getJarSnapshots([(new File("f")): new byte[0]]) >> [(new File("f")): snapshot]
        0 * _
    }
}
