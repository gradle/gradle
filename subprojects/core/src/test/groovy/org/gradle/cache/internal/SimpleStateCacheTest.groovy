/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.cache.Serializer
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification
import org.gradle.cache.DefaultSerializer

class SimpleStateCacheTest extends Specification {
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder()
    final FileLock lock = Mock()
    final Serializer<String> serializer = new DefaultSerializer<String>()
    final SimpleStateCache<String> cache = new SimpleStateCache<String>(tmpDir.file("state.bin"), lock, serializer)

    def "returns null when file does not exist"() {
        when:
        def result = cache.get()

        then:
        result == null
        1 * lock.readFromFile(!null) >> { it[0].call() }
    }
    
    def "get returns last value written to file"() {
        when:
        cache.set('some value')

        then:
        1 * lock.writeToFile(!null) >> { it[0].run() }
        tmpDir.file('state.bin').assertIsFile()

        when:
        def result = cache.get()

        then:
        result == 'some value'
        1 * lock.readFromFile(!null) >> { it[0].call() }
    }
}
