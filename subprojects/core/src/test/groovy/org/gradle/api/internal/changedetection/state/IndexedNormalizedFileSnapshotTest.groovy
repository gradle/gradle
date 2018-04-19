/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import spock.lang.Specification

class IndexedNormalizedFileSnapshotTest extends Specification {

    def content = MissingFileContentSnapshot.instance

    def "sorts by normalized path in lexicographical order"() {
        given:
        def unsorted = [
            new IndexedNormalizedFileSnapshot("/baz/fizz", 4, content),
            new IndexedNormalizedFileSnapshot("/foo/bar", 4, content),
            new IndexedNormalizedFileSnapshot("/foo/bar", 2, content),
            new IndexedNormalizedFileSnapshot("/foo/bar", 6, content),
            new NonNormalizedFileSnapshot("/fizz", content),
            new IndexedNormalizedFileSnapshot("/baz/bar", 4, content),
            new IndexedNormalizedFileSnapshot("/baz/fizz/buzz", 4, content),
        ]

        expect:
        unsorted.sort().collect { it.normalizedPath } == [
            "/bar",
            "/bar",
            "/fizz",
            "/fizz",
            "/fizz/buzz",
            "ar",
            "oo/bar",
        ]
    }

    def "equality checks the normalized path"() {
        given:
        def snapshot1 = new IndexedNormalizedFileSnapshot("/foo/bar", 4, content)
        def snapshot2 = new IndexedNormalizedFileSnapshot("/baz/bar", 4, content)

        expect:
        snapshot1 == snapshot2
    }

    def "equality checks the type"() {
        given:
        def snapshot1 = new IndexedNormalizedFileSnapshot("/foo/bar", 4, content)
        def snapshot2 = new NonNormalizedFileSnapshot("/bar", content)

        expect:
        snapshot1 != snapshot2
    }
}
