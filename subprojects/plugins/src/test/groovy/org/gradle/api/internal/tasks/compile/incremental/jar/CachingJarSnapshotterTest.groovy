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

import org.gradle.api.file.FileTree
import org.gradle.api.internal.hash.Hasher
import org.gradle.api.internal.tasks.compile.incremental.cache.IncrementalCompilationCache
import spock.lang.Specification
import spock.lang.Subject

class CachingJarSnapshotterTest extends Specification {

    def delegate = Mock(JarSnapshotter)
    def hasher = Mock(Hasher)
    def cache = Mock(IncrementalCompilationCache)

    @Subject snapshotter = new CachingJarSnapshotter(delegate, hasher, cache)

    def "creates new snapshot"() {
        def jar = new JarArchive(new File("jar"), Mock(FileTree))
        def hash = new byte[0]
        def snapshot = Mock(JarSnapshot)

        when: snapshotter.createSnapshot(jar)
        then:
        1 * hasher.hash(new File("jar")) >> hash
        1 * cache.loadSnapshot(hash) >> null
        1 * delegate.createSnapshot(jar) >> snapshot
        1 * cache.storeSnapshot(hash, snapshot)
        0 * _
    }

    def "loads snapshot from cache"() {
        def jar = new JarArchive(new File("jar"), Mock(FileTree))
        def hash = new byte[0]
        def snapshot = Mock(JarSnapshot)

        when: snapshotter.createSnapshot(jar)
        then:
        1 * hasher.hash(new File("jar")) >> hash
        1 * cache.loadSnapshot(hash) >> snapshot
        0 * _
    }
}