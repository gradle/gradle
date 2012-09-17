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
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

class UniquePathKeyFileStoreTest extends Specification {

    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder();
    Action<File> action = Mock()

    UniquePathKeyFileStore uniquePathKeyFileStore

    def setup() {
        uniquePathKeyFileStore = new UniquePathKeyFileStore(temporaryFolder.createDir("fsbase"))
    }

    def "add executes action if file does not exist"() {
        when:
        def fileInStore = uniquePathKeyFileStore.add("a/a", action)
        then:
        fileInStore != null
        1 * action.execute(_)
    }

    def "add skips action if file already exists"() {
        UniquePathKeyFileStore uniquePathKeyFileStore = new UniquePathKeyFileStore(temporaryFolder.createDir("fsbase"))
        setup:
        temporaryFolder.createFile("fsbase/a/a");
        when:
        def fileInStore = uniquePathKeyFileStore.add("a/a", action)
        then:
        fileInStore != null
        0 * action.execute(_)
    }

}
