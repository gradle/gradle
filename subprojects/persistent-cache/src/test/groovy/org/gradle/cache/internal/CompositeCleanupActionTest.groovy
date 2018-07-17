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

package org.gradle.cache.internal

import org.gradle.cache.CleanableStore
import org.gradle.cache.CleanupAction
import org.gradle.internal.time.CountdownTimer
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class CompositeCleanupActionTest extends Specification {

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    def cleanableStore = Stub(CleanableStore) {
        getBaseDir() >> temporaryFolder.getTestDirectory()
        getDisplayName() >> "My Cache"
    }
    def timer = Mock(CountdownTimer)

    def "calls configured cleanup actions for correct dirs"() {
        given:
        def firstCleanupAction = Mock(CleanupAction)
        def secondCleanupAction = Mock(CleanupAction)
        def subDir = temporaryFolder.file("subDir")

        when:
        CompositeCleanupAction.builder()
            .add(firstCleanupAction)
            .add(subDir, secondCleanupAction)
            .build()
            .clean(cleanableStore, timer)

        then:
        2 * timer.hasExpired() >> false
        1 * firstCleanupAction.clean(_, timer) >> { store, timer ->
            assert store.getBaseDir() == temporaryFolder.getTestDirectory()
        }
        1 * secondCleanupAction.clean(_, timer) >> { store, timer ->
            assert store.getBaseDir() == subDir
        }
    }

    def "aborts cleanup when timer has expired"() {
        given:
        def firstSubDir = temporaryFolder.file("first")
        def firstCleanupAction = Mock(CleanupAction)
        def secondSubDir = temporaryFolder.file("second")
        def secondCleanupAction = Mock(CleanupAction)

        when:
        CompositeCleanupAction.builder()
            .add(firstSubDir, firstCleanupAction)
            .add(secondSubDir, secondCleanupAction)
            .build()
            .clean(cleanableStore, timer)

        then:
        1 * timer.hasExpired() >> false
        1 * firstCleanupAction.clean(_, timer) >> { store, timer ->
            assert store.getBaseDir() == firstSubDir
        }
        1 * timer.hasExpired() >> true
        0 * secondCleanupAction.clean(_, _)
    }
}
