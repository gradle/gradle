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

import org.gradle.internal.Factory
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

class UUIDFileStoreTest extends Specification {

    @Rule TemporaryFolder tmp = new TemporaryFolder()

    def uuid = UUID.randomUUID()
    def factoryImpl = { uuid }
    def fsBase = tmp.createDir("store")

    UUIDFileStore fileStore = new UUIDFileStore(new PathKeyFileStore(fsBase), new Factory<UUID> () {
        UUID create() {
            factoryImpl.call()
        }
    })

    def "can move files to filestore"() {
        when:
        def entry = fileStore.move("a/b/c", tmp.createFile("f1") << "abc")

        then:
        fsBase.file("a/b/c/${uuid.toString()}").equals(entry.file)
        entry.file.name == uuid.toString()
        entry.file.text == "abc"

        when:
        uuid = UUID.randomUUID()

        and:
        entry = fileStore.move("", tmp.createFile("f2") << "def")

        then:
        fsBase.file("${uuid.toString()}").equals(entry.file)
        entry.file.text == "def"
        entry.file.name == uuid.toString()
    }

}
