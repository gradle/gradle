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

package org.gradle.internal.util

import spock.lang.Specification

class TrieTest extends Specification {

    def "empty trie is empty"() {
        def empty = Trie.from()
        expect:
        !empty.find("")
        !empty.find("alma")
    }

    def "can find exact match"() {
        def trie = Trie.from("aaa", "bbb")
        expect:
        trie.find("aaa")
        trie.find("bbb")
        !trie.find("ccc")
    }

    def "can find prefix match"() {
        def trie = Trie.from("aaa", "bbb")
        expect:
        !trie.find("a")
        !trie.find("aa")
        trie.find("aaaa")
        trie.find("aaab")
    }

}
