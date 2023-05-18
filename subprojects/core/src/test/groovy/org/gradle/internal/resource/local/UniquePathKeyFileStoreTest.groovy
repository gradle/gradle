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
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class UniquePathKeyFileStoreTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass());
    Action<File> action = Mock()

    UniquePathKeyFileStore uniquePathKeyFileStore

    def setup() {
        uniquePathKeyFileStore = new UniquePathKeyFileStore(TestUtil.checksumService, temporaryFolder.createDir("fsbase"))
    }

    def "add executes action if file does not exist"() {
        def file = temporaryFolder.file("fsbase/a/a");

        when:
        def fileInStore = uniquePathKeyFileStore.add("a/a", action)

        then:
        fileInStore.file == file
        1 * action.execute(file) >> { File f -> f.text = 'hi' }
    }

    def "add skips action if file already exists"() {
        setup:
        def file = temporaryFolder.createFile("fsbase/a/a");

        when:
        def fileInStore = uniquePathKeyFileStore.add("a/a", action)

        then:
        fileInStore.file == file
        0 * action.execute(_)
    }

    def "move returns existing file if it already exists"() {
        setup:
        def source = temporaryFolder.createFile("some-file")
        def file = temporaryFolder.createFile("fsbase/a/a");
        file.text = 'existing content'

        when:
        def fileInStore = uniquePathKeyFileStore.move("a/a", source)

        then:
        fileInStore.file == file
        file.text == 'existing content'
        !source.exists()
    }

    def "move adds file if it does not exist"() {
        setup:
        def source = temporaryFolder.createFile("some-file")
        def file = temporaryFolder.file("fsbase/a/a");

        when:
        def fileInStore = uniquePathKeyFileStore.move("a/a", source)

        then:
        fileInStore.file == file
        !source.exists()
    }
}
