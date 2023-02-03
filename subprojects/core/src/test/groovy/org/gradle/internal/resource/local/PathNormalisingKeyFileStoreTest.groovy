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

package org.gradle.internal.resource.local

import org.gradle.api.Action
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class PathNormalisingKeyFileStoreTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider dir = new TestNameTestDirectoryProvider(getClass())
    TestFile fsBase
    PathNormalisingKeyFileStore store

    def pathCounter = 0

    def setup() {
        fsBase = dir.createDir("fs")
        store = new PathNormalisingKeyFileStore(fsBase, TestUtil.checksumService)
    }

    def "can move to filestore"() {
        when:
        store.move("!.zip", file("abc"))
        store.move("  ", file("def"))

        then:
        fsBase.file("_.zip").text == "abc"
        fsBase.file("__").text == "def"
    }

    def "can add to filestore"() {
        when:
        store.add("!.zip", { File file -> file.text = "abc" } as Action<File>)
        store.add("  ", { File file -> file.text = "def" } as Action<File>)
        then:
        fsBase.file("_.zip").text == "abc"
        fsBase.file("__").text == "def"
    }

    def "can overwrite entry"() {
        when:
        store.move("!", file("abc"))
        store.move(" ", file("def"))

        then:
        fsBase.file("_").text == "def"
    }

    def "creates intermediary directories"() {
        when:
        store.move("a/!/c", file("abc"))
        store.move("a/ /d", file("abd"))
        store.move("a/c/(", file("aca"))

        then:
        fsBase.file("a/_").directory
        fsBase.file("a/_/c").text == "abc"
        fsBase.file("a/c/_").text == "aca"
    }

    def "can search via globs"() {
        when:
        store.move("a/!/a", file("a"))
        store.move("a/ /b", file("b"))
        store.move("a/b/&", file("c"))

        then:
        store.search("**/a").size() == 1
        store.search("*/ /*").size() == 2
        store.search("a/b/_").size() == 1
    }

    def file(String content, String path = "f${pathCounter++}") {
        dir.createFile(path) << content
    }

}
