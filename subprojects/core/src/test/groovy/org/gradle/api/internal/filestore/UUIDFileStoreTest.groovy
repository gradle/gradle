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

    UUIDFileStore fileStore = new UUIDFileStore(new UniquePathFileStore(tmp.createDir("store")), new Factory<UUID> () {
        UUID create() {
            factoryImpl.call()
        }
    })

    def "can move files to filestore"() {
        given:
        def f1 = tmp.createFile("f1") << "abc"
        def f2 = tmp.createFile("f2") << "def"

        when:
        def entry = fileStore.add(f1)

        then:
        entry.file.name == uuid.toString()
        entry.file.text == "abc"

        when:
        uuid = UUID.randomUUID()

        and:
        entry = fileStore.add(f2)

        then:
        entry.file.text == "def"
        entry.file.name == uuid.toString()
    }

}
