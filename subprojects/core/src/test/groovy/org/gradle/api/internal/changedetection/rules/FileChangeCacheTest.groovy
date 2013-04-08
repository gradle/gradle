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

package org.gradle.api.internal.changedetection.rules

import spock.lang.Specification;

public class FileChangeCacheTest extends Specification {
    def cache = new FileChangeCache(2)

    def "is not complete initially"() {
        expect:
        !cache.iterator().hasNext()
        !cache.complete
    }

    def "empty cache is complete when seen all entries"() {
        when:
        cache.hasSeenAllChanges()

        then:
        !cache.iterator().hasNext()
        cache.complete
    }

    def "caches changes up to max number"() {
        when:
        cache.cache(change("one"))
        cache.cache(change("two"))

        then:
        cache.collect({it.message}) == ["one", "two"]
        !cache.complete

        when:
        cache.hasSeenAllChanges()

        then:
        cache.complete
    }

    def "does not cache changes beyond max number"() {
        when:
        cache.cache(change("one"))
        cache.cache(change("two"))
        cache.cache(change("three"))

        then:
        cache.collect({it.message}) == ["one", "two"]
        !cache.complete

        when:
        cache.hasSeenAllChanges()

        then:
        !cache.complete
    }

    def "adding another change unsets the hasSeenAllChanges flag"() {
        when:
        cache.cache(change("one"))
        cache.hasSeenAllChanges()

        then:
        cache.collect({it.message}) == ["one"]
        cache.complete

        when:
        cache.cache(change("two"))

        then:
        cache.collect({it.message}) == ["one", "two"]
        !cache.complete

        when:
        cache.hasSeenAllChanges()

        then:
        cache.complete
    }

    def change(def message) {
        Stub(FileChange) {
            getMessage() >> message
        }
    }
}
