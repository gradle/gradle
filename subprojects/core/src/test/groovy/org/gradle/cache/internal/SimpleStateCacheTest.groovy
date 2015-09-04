/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.cache.internal

import org.gradle.cache.PersistentStateCache
import org.gradle.internal.serialize.DefaultSerializer
import org.gradle.internal.serialize.Serializer
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.cache.internal.DefaultFileLockManagerTestHelper.*

class SimpleStateCacheTest extends Specification {
    @Rule public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final FileAccess fileAccess = Mock()
    final File file = tmpDir.file("state.bin")
    final SimpleStateCache<String> cache = createStateCache(fileAccess)

    private SimpleStateCache createStateCache(FileAccess fileAccess, File file = file, Serializer serializer = new DefaultSerializer()) {
        return new SimpleStateCache(file, fileAccess, serializer)
    }

    def "returns null when file does not exist"() {
        when:
        def result = cache.get()

        then:
        result == null
        1 * fileAccess.readFile(!null) >> { it[0].create() }
    }
    
    def "get returns last value written to file"() {
        when:
        cache.set('some value')

        then:
        1 * fileAccess.writeFile(!null) >> { it[0].run() }
        tmpDir.file('state.bin').assertIsFile()

        when:
        def result = cache.get()

        then:
        result == 'some value'
        1 * fileAccess.readFile(!null) >> { it[0].create() }
    }

    def "update provides access to cached value"() {
        when:
        cache.set("foo")

        then:
        1 * fileAccess.writeFile(!null) >> { it[0].run() }

        when:
        cache.update({ value ->
            assert value == "foo"
            return "foo bar"
        } as PersistentStateCache.UpdateAction)

        then:
        1 * fileAccess.updateFile(!null) >> { it[0].run() }

        when:
        def result = cache.get()

        then:
        result == "foo bar"
        1 * fileAccess.readFile(!null) >> { it[0].create() }
    }

    def "update does not explode when no existing value"() {
        when:
        cache.update({ value ->
            assert value == null
            return "bar"
        } as PersistentStateCache.UpdateAction)

        then:
        1 * fileAccess.updateFile(!null) >> { it[0].run() }

        when:
        def result = cache.get()

        then:
        result == "bar"
        1 * fileAccess.readFile(!null) >> { it[0].create() }
    }
    
    def "can set value when file integrity is violated"() {
        given:
        def cache = createStateCache(createOnDemandFileLock(file))
        
        and:
        cache.set "a"
        
        expect:
        cache.get() == "a"
        
        when:
        unlockUncleanly(file)

        then:
        isIntegrityViolated(file)

        when:
        cache.set "b"

        then:
        cache.get() == "b"
    }
}
