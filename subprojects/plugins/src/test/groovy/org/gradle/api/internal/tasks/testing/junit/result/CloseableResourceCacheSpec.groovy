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

package org.gradle.api.internal.tasks.testing.junit.result

import org.gradle.api.Action
import org.gradle.api.internal.ClosureBackedAction
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class CloseableResourceCacheSpec extends Specification {

    def @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()

    def created = []
    def closed = []

    def cache = new CloseableResourceCache(2, new CloseableResourceCache.ResourceCreator() {
        Closeable create(Object key) throws Exception {
            def closeable = new Closeable() {
                @Override
                String toString() {
                    key
                }

                void close() throws IOException {
                    CloseableResourceCacheSpec.this.closed << key
                }
            }
            created << closeable
            closeable
        }
    })

    def cleanup() {
        cache.closeAll()
    }

    def "keeps n files open"() {
        when:
        cache.with("1", hasId("1"))
        cache.with("2", hasId("2"))

        then:
        closed.empty
        created.size() == 2

        when:
        cache.with("1", hasId("1"))

        then:
        closed.empty
        created.size() == 2

        when:
        cache.with("3", hasId("3"))

        then:
        closed == ["1"]
        created.size() == 3

        when:
        cache.with("4", hasId("4"))

        then:
        closed == ["1", "2"]
        created.size() == 4

        when:
        cache.closeAll()

        then:
        closed == ["1", "2", "3", "4"]
    }

    def "closes all when error occurs in action"() {
        when:
        cache.with("1", hasId("1"))
        cache.with("2", action { throw new Exception() })

        then:
        closed == ["1", "2"]
    }

    Action action(Closure closure) {
        new ClosureBackedAction(closure)
    }

    Action hasId(String id) {
        action {
            assert it.toString() == id
        }
    }
}
