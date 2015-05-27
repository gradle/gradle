/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.filewatch

import org.gradle.api.internal.file.FileSystemSubset
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

@Requires(TestPrecondition.JDK7_OR_LATER)
class DefaultFileSystemChangeWaiterTest extends ConcurrentSpec {

    @Rule
    TestNameTestDirectoryProvider testDirectory

    def "can wait for filesystem change"() {
        when:
        def w = new DefaultFileSystemChangeWaiter(executorFactory, new DefaultFileWatcherFactory(executorFactory))
        def f = FileSystemSubset.builder().add(testDirectory.testDirectory).build()
        def c = new DefaultBuildCancellationToken()

        start {
            w.wait(f, c) {
                instant.notified
            }
            instant.done
        }

        then:
        waitFor.notified

        when:
        testDirectory.file("new") << "change"

        then:
        waitFor.done
    }

    def "escapes on cancel"() {
        when:
        def w = new DefaultFileSystemChangeWaiter(executorFactory, new DefaultFileWatcherFactory(executorFactory))
        def f = FileSystemSubset.builder().add(testDirectory.testDirectory).build()
        def c = new DefaultBuildCancellationToken()

        start {
            w.wait(f, c) {
                instant.notified
            }
            instant.done
        }

        then:
        waitFor.notified

        when:
        c.cancel()

        then:
        waitFor.done
    }

}
