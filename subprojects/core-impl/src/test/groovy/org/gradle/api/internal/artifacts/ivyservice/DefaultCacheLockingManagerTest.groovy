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
package org.gradle.api.internal.artifacts.ivyservice

import java.util.concurrent.Callable
import org.gradle.cache.internal.FileLock
import org.gradle.cache.internal.FileLockManager
import org.gradle.cache.internal.FileLockManager.LockMode
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

class DefaultCacheLockingManagerTest extends Specification {
    @Rule final TemporaryFolder tmpDir = new TemporaryFolder()
    final FileLockManager fileLockManager = Mock()
    final ArtifactCacheMetaData metaData = Mock()
    final File cacheDir = tmpDir.file("cache-dir")
    final File cacheFile = cacheDir.file("cache-file.bin")
    DefaultCacheLockingManager lockingManager

    def setup() {
        _ * metaData.cacheDir >> cacheDir
        lockingManager = new DefaultCacheLockingManager(fileLockManager, metaData)
    }

    def "executes action and returns result"() {
        Callable<String> action = Mock()

        when:
        def result = lockingManager.withCacheLock("some operation", action)

        then:
        result == 'result'

        and:
        1 * action.call() >> 'result'
        0 * _._
    }

    def "locks metadata file when metadata cache is used and releases lock at the end of the action"() {
        Callable<String> action = Mock()
        FileLock lock = Mock()
        def cache = lockingManager.createCache(cacheFile, String, Integer)

        when:
        lockingManager.withCacheLock("some operation", action)

        then:
        1 * action.call() >> {
            cache.get("key")
        }
        1 * fileLockManager.lock(cacheDir, LockMode.Exclusive, "artifact cache '$cacheDir'") >> lock
        _ * lock.readFromFile(_)
        _ * lock.writeToFile(_)
        1 * lock.close()
        0 * _._
    }
}
