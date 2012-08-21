/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.filestore

import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile
import org.junit.Rule
import spock.lang.Specification

class PathKeyFileStoreTest extends Specification {

    @Rule TemporaryFolder dir = new TemporaryFolder()
    TestFile fsBase
    PathKeyFileStore store

    def pathCounter = 0

    def setup() {
        fsBase = dir.createDir("fs")
        store = new PathKeyFileStore(fsBase)
    }

    def "can add to filestore"() {
        when:
        store.add("a", file("abc"))
        store.add("b", file("def"))

        then:
        fsBase.file("a").text == "abc"
        fsBase.file("b").text == "def"
    }

    def "can overwrite entry"() {
        when:
        store.add("a", file("abc"))
        store.add("a", file("def"))

        then:
        fsBase.file("a").text == "def"
    }

    def "creates intermediary directories"() {
        when:
        store.add("a/b/c", file("abc"))
        store.add("a/b/d", file("abd"))
        store.add("a/c/a", file("aca"))

        then:
        fsBase.file("a/b").directory
        fsBase.file("a/b/c").text == "abc"
        fsBase.file("a/c/a").text == "aca"
    }

    def "can search via globs"() {
        when:
        store.add("a/a/a", file("a"))
        store.add("a/a/b", file("b"))
        store.add("a/b/a", file("c"))

        then:
        store.search("**/a").size() == 2
        store.search("*/b/*").size() == 1
        store.search("a/b/a").size() == 1
    }

    def file(String content, String path = "f${pathCounter++}") {
        dir.createFile(path) << content
    }
}
