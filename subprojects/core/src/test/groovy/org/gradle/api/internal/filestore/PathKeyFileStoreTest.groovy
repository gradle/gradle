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

import org.gradle.api.Action
import org.gradle.api.GradleException
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
        fsBase = dir.file("fs")
        store = new PathKeyFileStore(fsBase)
    }

    def "move vs copy"() {
        given:
        createFile("a", "a").exists()
        createFile("b", "b").exists()

        when:
        store.move("a", dir.file("a"))
        store.copy("b", dir.file("b"))

        then:
        !dir.file("a").exists()
        dir.file("b").exists()
    }

    def "can move to filestore"() {
        when:
        store.move("a", createFile("abc"))
        store.move("b", createFile("def"))

        then:
        fsBase.file("a").text == "abc"
        fsBase.file("b").text == "def"
    }

    def "can add to filestore"() {
        when:
        store.add("a", { File f -> f.text = "abc"} as Action<File>)
        store.add("b", { File f -> f.text = "def"} as Action<File>)
        then:
        fsBase.file("a").text == "abc"
        fsBase.file("b").text == "def"
    }

    def "add throws GradleException if exception in action occured"() {
        when:
        store.add("a", { File f -> throw new Exception("TestException")} as Action<File>)
        then:
        thrown(GradleException)
        !fsBase.file("a").exists()
        !fsBase.file("a.fslock").exists()
    }

    def "can get from filestore"() {
        when:
        createFile("abc", "fs/a")
        then:
        store.get("a") != null
        store.get("b") == null
    }

    def "get cleans up filestore"() {
        when:
        createFile("abc", "fs/a").exists()
        createFile("lock", "fs/a.fslck").exists()
        then:
        store.get("a") == null
        store.get("a.fslock") == null
    }

    def "can overwrite stale files "() {
        given:
        createFile("abc", "fs/a").exists()
        createFile("lock", "fs/a.fslck").exists()
        when:
        store.add("a", { File f -> f.text = "def"} as Action<File>)
        then:
        store.get("a").file.text == "def"
    }

    def "get on stale file with marker removes file from filestore"() {
        when:
        createFile("abc", "fs/a")
        createFile("def", "fs/b")
        then:
        store.get("a").file.text == "abc"
        store.get("b").file.text == "def"
    }

    def "can overwrite entry"() {
        when:
        store.move("a", createFile("abc"))
        store.move("a", createFile("def"))

        then:
        fsBase.file("a").text == "def"
    }

    def "creates intermediary directories"() {
        when:
        store.move("a/b/c", createFile("abc"))
        store.move("a/b/d", createFile("abd"))
        store.move("a/c/a", createFile("aca"))

        then:
        fsBase.file("a/b").directory
        fsBase.file("a/b/c").text == "abc"
        fsBase.file("a/c/a").text == "aca"
    }

    def "can search via globs"() {
        when:
        store.move("a/a/a", createFile("a"))
        store.move("a/a/b", createFile("b"))
        store.move("a/b/a", createFile("c"))

        then:
        store.search("**/a").size() == 2
        store.search("*/b/*").size() == 1
        store.search("a/b/a").size() == 1
    }

    def "search ignores stale entries with marker file"() {
        when:
        store.move("a/a/a", createFile("a"))
        store.move("a/b/b", createFile("b"))
        store.move("a/c/c", createFile("c"))
        createFile("lock", "fs/a/b/b.fslck")
        def search = store.search("**/*")
        then:
        search.size() == 2
        search.collect {entry -> entry.file.name}.sort() == ["a", "c"]
    }

    def "move filestore"() {
        given:
        def a = store.move("a", createFile("abc"))
        def b = store.move("b", createFile("def"))

        expect:
        a.file == dir.file("fs/a")
        b.file == dir.file("fs/b")

        when:
        store.moveFilestore(dir.file("new-store"))

        then:
        store.baseDir == dir.file("new-store")

        and:
        a.file == dir.file("new-store/a")
        b.file == dir.file("new-store/b")

        !dir.file("fs").exists()
    }

    def "can move filestore that doesn't exist yet"() {
        expect:
        !store.baseDir.exists()

        when:
        store.moveFilestore(dir.file("new-filestore"))

        then:
        notThrown Exception
    }

    def createFile(String content, String path = "f${pathCounter++}") {
        dir.createFile(path) << content
    }
}
