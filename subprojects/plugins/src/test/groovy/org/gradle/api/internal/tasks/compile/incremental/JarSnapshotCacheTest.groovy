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

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class JarSnapshotCacheTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()
    @Subject cache = new JarSnapshotCache(temp.file("cache.bin"))

    def "empty cache"() {
        expect:
        !cache.getSnapshot(new File("foo.jar"))
    }

    def "caches snapshots"() {
        when:
        cache.putSnapshots([(new File("foo.jar")): new JarSnapshot(["Foo": "f".bytes])])

        then:
        cache.getSnapshot(new File("foo.jar")).classHashes == ["Foo": "f".bytes]
    }

    def "caches snapshots in file"() {
        when:
        cache.putSnapshots([(new File("foo.jar")): new JarSnapshot(["Foo": "f".bytes])])

        then:
        def cache2 = new JarSnapshotCache(temp.file("cache.bin"))
        cache2.getSnapshot(new File("foo.jar")).classHashes == ["Foo": "f".bytes]
    }
}
