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
class DefaultPathKeyFileStoreTest extends Specification {
    @Rule TestNameTestDirectoryProvider dir = new TestNameTestDirectoryProvider(getClass())
    TestFile fsBase
    PathKeyFileStore store

    def pathCounter = 0

    def setup() {
        fsBase = dir.file("fs")
        store = new DefaultPathKeyFileStore(TestUtil.checksumService, fsBase)
    }

    def "can move file to filestore"() {
        def a = createFile("abc")
        def b = createFile("def")

        when:
        // leading slash does not mean absolute path
        store.move("/a", a)
        store.move("b", b)

        then:
        def storedA = store.get("a")
        storedA.file.text == "abc"
        storedA.file == fsBase.file("a")
        !a.exists()

        def storedB = store.get("b")
        storedB.file.text == "def"
        storedB.file == fsBase.file("b")
        !b.exists()
    }

    def "can move directory to filestore"() {
        def a = dir.createDir("a")
        a.file("child-1").createFile()
        a.file("dir/child-2").createFile()

        when:
        store.move("a", a)

        then:
        def stored = store.get("a")
        stored.file.directory
        stored.file == fsBase.file("a")
        fsBase.file("a").assertHasDescendants("child-1", "dir/child-2")
        !a.exists()
    }

    def "can add file to filestore"() {
        when:
        store.add("a", { File f -> f.text = "abc"} as Action<File>)
        store.add("b", { File f -> f.text = "def"} as Action<File>)

        then:
        def storedA = store.get("a")
        storedA.file.text == "abc"
        storedA.file == fsBase.file("a")

        def storedB = store.get("b")
        storedB.file.text == "def"
        storedB.file == fsBase.file("b")
    }

    def "can add directory to filestore"() {
        when:
        store.add("a") { File f ->
            f.mkdirs()
            new File(f, "a").text = "abc"
        }
        store.add("b") { File f ->
            f.mkdirs()
            new File(f, "b").text = "def"
        }

        then:
        def storedA = store.get("a")
        storedA.file == fsBase.file("a")
        fsBase.file("a").assertHasDescendants("a")

        def storedB = store.get("b")
        storedB.file == fsBase.file("b")
        fsBase.file("b").assertHasDescendants("b")
    }

    def "add throws FileStoreAddActionException if exception in action occurred and cleans up"() {
        def failure = new RuntimeException("TestException")

        when:
        store.add("a", { File f ->
            throw failure
        } as Action<File>)
        then:
        def e = thrown(FileStoreAddActionException)
        e.cause == failure

        !fsBase.file("a").exists()
        !fsBase.file("a.fslock").exists()
    }

    def "cleans up left-over files when action fails"() {
        when:
        store.add("a", { File f ->
            new File(f, "child").text = "delete-me"
            throw new RuntimeException("TestException")
        } as Action<File>)

        then:
        thrown(FileStoreAddActionException)
        !fsBase.file("a").exists()
        !fsBase.file("a.fslock").exists()
    }

    def "can get from backing filestore"() {
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

    def "can overwrite file entry"() {
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

    def createFile(String content, String path = "f${pathCounter++}") {
        dir.createFile(path) << content
    }
}
