/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.resource.cached

import org.gradle.api.Action
import org.gradle.internal.file.FileAccessTracker
import org.gradle.internal.resource.local.LocallyAvailableResource
import spock.lang.Specification
import spock.lang.Subject

class TwoStageExternalResourceFileStoreTest extends Specification {
    def readStore = Mock(ExternalResourceFileStore)
    def writeStore = Mock(ExternalResourceFileStore)

    String key = "key"
    def file = Stub(File)
    def action = Stub(Action)

    @Subject
    TwoStageExternalResourceFileStore twoStageStore = new TwoStageExternalResourceFileStore(readStore, writeStore)

    def "storing an artifact uses the writable store"() {
        when:
        twoStageStore.move(key, file)

        then:
        1 * writeStore.move(key, file)
        0 * readStore.move(_, _)

        when:
        twoStageStore.add(key, action)

        then:
        1 * writeStore.add(key, action)
        0 * readStore.add(key, action)
    }

    def "searches in both read and write stores"() {
        setup:
        def r1 = Stub(LocallyAvailableResource)
        def r2 = Stub(LocallyAvailableResource)
        def r3 = Stub(LocallyAvailableResource)
        1 * writeStore.search(key) >> {
            [r1] as Set
        }
        1 * readStore.search(key) >> {
            [r2, r3] as Set
        }

        when:
        def result = twoStageStore.search(key)

        then:
        result == [r1, r2, r3] as Set
    }

    def "file access tracker delegates to both trackers"() {
        def fat1 = Mock(FileAccessTracker)
        def fat2 = Mock(FileAccessTracker)

        when:
        twoStageStore.getFileAccessTracker().markAccessed(file)

        then:
        1 * readStore.getFileAccessTracker() >> fat1
        1 * writeStore.getFileAccessTracker() >> fat2
        1 * fat1.markAccessed(file)
        1 * fat2.markAccessed(file)
    }
}
