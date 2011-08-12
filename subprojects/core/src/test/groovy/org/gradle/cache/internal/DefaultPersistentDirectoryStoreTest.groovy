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
import org.gradle.util.TemporaryFolder
import org.junit.Rule

class DefaultPersistentDirectoryStoreTest extends Specification {
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();

    def "creates directory and cache.properties file if it does not exist"() {
        given:
        def dir = tmpDir.file('dir')
        dir.assertDoesNotExist()

        when:
        def store = new DefaultPersistentDirectoryStore(dir)

        then:
        dir.assertIsDir()
        dir.file('cache.properties').assertIsFile()
    }

    def "does nothing when directory and cache.properties file already exist"() {
        given:
        def dir = tmpDir.dir
        dir.assertIsDir()
        dir.file('cache.properties').createFile()

        when:
        def store = new DefaultPersistentDirectoryStore(dir)

        then:
        notThrown(RuntimeException)
    }
}
