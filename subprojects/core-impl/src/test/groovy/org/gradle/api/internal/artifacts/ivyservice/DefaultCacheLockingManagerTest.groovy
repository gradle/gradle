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

import org.gradle.api.internal.Factory
import org.gradle.cache.internal.FileLock
import org.gradle.cache.internal.FileLockManager
import org.gradle.cache.internal.FileLockManager.LockMode
import org.gradle.util.ConcurrentSpecification
import org.gradle.util.TemporaryFolder
import org.junit.Rule

class DefaultCacheLockingManagerTest extends ConcurrentSpecification {
    @Rule final TemporaryFolder tmpDir = new TemporaryFolder()
    final FileLockManager fileLockManager = Mock()
    final ArtifactCacheMetaData metaData = Mock()
    final File cacheDir = tmpDir.file("cache-dir")
    final File cacheFile = cacheDir.file("cache-file.bin")
    final FileLock lock = Mock()
    DefaultCacheLockingManager lockingManager

    def setup() {
        _ * metaData.cacheDir >> cacheDir
        lockingManager = new DefaultCacheLockingManager(fileLockManager, metaData)
    }

    def "executes cache action and returns result"() {
        Factory<String> action = Mock()

        when:
        def result = lockingManager.useCache("some operation", action)

        then:
        result == 'result'

        and:
        1 * action.create() >> 'result'
        0 * _._
    }

    def "locks cache directory when a cache is used and releases lock at the end of the cache action"() {
        Factory<String> action = Mock()
        def cache = lockingManager.createCache(cacheFile, String, Integer)

        when:
        lockingManager.useCache("some operation", action)

        then:
        1 * action.create() >> {
            cache.get("key")
        }
        1 * fileLockManager.lock(cacheDir, LockMode.Exclusive, "artifact cache '$cacheDir'", "some operation") >> lock
        _ * lock.readFromFile(_)

        and:
        _ * lock.writeToFile(_)
        1 * lock.close()
        0 * _._
    }

    def "unlocks metadata file during long running operation"() {
        Factory<String> action = Mock()
        Factory<String> longRunningAction = Mock()
        def cache = lockingManager.createCache(cacheFile, String, Integer)

        when:
        lockingManager.useCache("some operation", action)

        then:
        1 * action.create() >> {
            cache.get("key")
            lockingManager.longRunningOperation("nested", longRunningAction)
            cache.get("key")
        }
        1 * longRunningAction.create()
        2 * fileLockManager.lock(cacheDir, LockMode.Exclusive, "artifact cache '$cacheDir'", "some operation") >> lock
        _ * lock.readFromFile(_)
        _ * lock.writeToFile(_)
        2 * lock.close()
        0 * _._
    }

    def "cannot run long running operation from outside cache action"() {
        when:
        lockingManager.longRunningOperation("operation", Mock(Factory))

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot start long running operation, as the artifact cache has not been locked.'
    }

    def "cannot use cache from within long running operation"() {
        Factory<String> action = Mock()
        Factory<String> longRunningAction = Mock()
        def cache = lockingManager.createCache(cacheFile, String, Integer)

        when:
        lockingManager.useCache("some operation", action)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot use cache outside a unit of work.'

        and:
        1 * action.create() >> {
            lockingManager.longRunningOperation("nested", longRunningAction)
        }
        1 * longRunningAction.create() >> {
            cache.get("key")
        }
        0 * _._
    }

    def "can execute cache action from within long running operation"() {
        Factory<String> action = Mock()
        Factory<String> longRunningAction = Mock()
        Factory<String> nestedAction = Mock()
        def cache = lockingManager.createCache(cacheFile, String, Integer)

        when:
        lockingManager.useCache("some operation", action)

        then:
        1 * action.create() >> {
            cache.get("key")
            lockingManager.longRunningOperation("nested", longRunningAction)
        }
        1 * longRunningAction.create() >> {
            lockingManager.useCache("nested 2", nestedAction)
        }
        1 * nestedAction.create() >> {
            cache.get("key")
        }
        1 * fileLockManager.lock(cacheDir, LockMode.Exclusive, "artifact cache '$cacheDir'", "some operation") >> lock
        1 * fileLockManager.lock(cacheDir, LockMode.Exclusive, "artifact cache '$cacheDir'", "nested 2") >> lock
        _ * lock.readFromFile(_)
        _ * lock.writeToFile(_)
        2 * lock.close()
        0 * _._
    }

    def "can execute long running operation from within long running operation"() {
        Factory<String> action = Mock()
        Factory<String> longRunningAction = Mock()
        Factory<String> nestedAction = Mock()
        def cache = lockingManager.createCache(cacheFile, String, Integer)

        when:
        lockingManager.useCache("some operation", action)

        then:
        1 * action.create() >> {
            cache.get("key")
            lockingManager.longRunningOperation("nested", longRunningAction)
        }
        1 * longRunningAction.create() >> {
            lockingManager.longRunningOperation("nested 2", nestedAction)
        }
        1 * nestedAction.create()
        1 * fileLockManager.lock(cacheDir, LockMode.Exclusive, "artifact cache '$cacheDir'", "some operation") >> lock
        _ * lock.readFromFile(_)
        _ * lock.writeToFile(_)
        1 * lock.close()
        0 * _._
    }

    def "can execute cache action from within cache action"() {
        Factory<String> action = Mock()
        Factory<String> nestedAction = Mock()
        def cache = lockingManager.createCache(cacheFile, String, Integer)

        when:
        lockingManager.useCache("some operation", action)

        then:
        1 * action.create() >> {
            cache.get("key")
            lockingManager.useCache("nested", nestedAction)
        }
        1 * nestedAction.create() >> {
            cache.get("key")
        }
        1 * fileLockManager.lock(cacheDir, LockMode.Exclusive, "artifact cache '$cacheDir'", "some operation") >> lock
        _ * lock.readFromFile(_)
        _ * lock.writeToFile(_)
        1 * lock.close()
        0 * _._
    }
}
