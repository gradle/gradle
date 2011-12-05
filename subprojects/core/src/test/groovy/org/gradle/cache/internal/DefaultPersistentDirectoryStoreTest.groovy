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

import org.gradle.os.jna.NativeEnvironment
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

class DefaultPersistentDirectoryStoreTest extends Specification {
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();
    final lockManager = new DefaultFileLockManager(new DefaultProcessMetaDataProvider(NativeEnvironment.current()))
    final dir = tmpDir.file("dir")
    final store = new DefaultPersistentDirectoryStore(dir, "<display>", FileLockManager.LockMode.Shared, lockManager)

    def cleanup() {
        store.close()
    }

    def "has useful toString() implementation"() {
        expect:
        store.toString() == "<display>"
    }

    def "creates directory if it does not exist"() {
        given:
        dir.assertDoesNotExist()

        when:
        store.open()

        then:
        dir.assertIsDir()
    }

    def "does nothing when directory already exists"() {
        given:
        dir.createDir()

        when:
        store.open()

        then:
        notThrown(RuntimeException)
    }
}
