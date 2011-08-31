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

import org.gradle.cache.DefaultSerializer
import org.gradle.cache.PersistentStateCache.UpdateAction
import org.gradle.cache.Serializer
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

class SimpleStateCacheFunctionalTest extends Specification {
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder()
    final Serializer<String> serializer = new DefaultSerializer<String>()

    FileLock lock
    SimpleStateCache<String> cache

    def prepare() {
        def file = tmpDir.file("state.bin")
        lock = new OnDemandFileLock(file, "cache for testing", new DefaultFileLockManager())
        cache = new SimpleStateCache<String>(file, lock, serializer)
    }

    def "provides access to cached value"() {
        prepare()

        when:
        cache.set("foo")

        then:
        cache.get() == "foo"

        when:
        cache.update({
            it + " bar"
        } as UpdateAction)

        then:
        cache.get() == "foo bar"
    }
}
