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

import org.gradle.cache.FileAccess
import org.gradle.cache.ObjectHolder
import org.gradle.internal.file.Chmod
import org.gradle.internal.serialize.DefaultSerializer
import org.gradle.internal.serialize.Serializer
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.cache.internal.DefaultFileLockManagerTestHelper.createOnDemandFileLock
import static org.gradle.cache.internal.DefaultFileLockManagerTestHelper.isIntegrityViolated
import static org.gradle.cache.internal.DefaultFileLockManagerTestHelper.unlockUncleanly

class FileBackedObjectHolderTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    final FileAccess fileAccess = Mock()
    final Chmod chmod = Mock()
    final File file = tmpDir.file("state.bin")
    final FileBackedObjectHolder<String> cache = createStateCache(fileAccess)

    private FileBackedObjectHolder createStateCache(FileAccess fileAccess, File file = file, Serializer serializer = new DefaultSerializer()) {
        return new FileBackedObjectHolder(file, fileAccess, serializer, chmod)
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

    def "makes file accessable only to user on write"() {
        when:
        cache.set('some value')

        then:
        1 * fileAccess.writeFile(!null) >> { it[0].run() }
        1 * chmod.chmod(file.parentFile, 0700)
        1 * chmod.chmod(file, 0600)
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
        } as ObjectHolder.UpdateAction)

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
        } as ObjectHolder.UpdateAction)

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

    def "maybeUpdate only writes back changes from update"() {
        given:
        // Note: we are using invocation of the serializer as an indicator of
        // file read/write activity.
        def serializer = Spy(DefaultSerializer)
        def cache = createStateCache(createOnDemandFileLock(file), file, serializer)

        when:
        cache.set "a"

        then:
        1 * serializer.write(_, "a")

        when: // same value is not written
        def r = cache.maybeUpdate { it }

        then:
        r == "a"
        1 * serializer.read(_)

        and:
        0 * serializer.write(_, _)

        when: // different value is written back
        r = cache.maybeUpdate { "b" }

        then:
        1 * serializer.read(_)

        and:
        1 * serializer.write(_, "b")

        and:
        r == "b"

        when: // same logical value, but different object
        r = cache.maybeUpdate { new String("b") }

        then:
        1 * serializer.read(_)

        and:
        0 * serializer.write(_, _)

        and:
        r == "b"
    }
}
