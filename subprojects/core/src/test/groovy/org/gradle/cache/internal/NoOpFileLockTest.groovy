/*
 * Copyright 2011 the original author or authors.
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

import spock.lang.Specification
import java.util.concurrent.Callable

class NoOpFileLockTest extends Specification {
    final NoOpFileLock lock = new NoOpFileLock()

    def "executes action on write"() {
        Runnable action = Mock()

        when:
        lock.writeToFile(action)

        then:
        1 * action.run()
    }

    def "executes action on read"() {
        Callable<String> action = Mock()

        when:
        def result = lock.readFromFile(action)

        then:
        1 * action.call() >> "result"

        and:
        result == "result"
    }
}
