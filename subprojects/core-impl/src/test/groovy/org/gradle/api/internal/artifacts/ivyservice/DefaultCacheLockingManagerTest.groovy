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
    final File cacheDir = tmpDir.file("cache-dir")
    final DefaultCacheLockingManager lockingManager = new DefaultCacheLockingManager(fileLockManager)

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

    def "acquires file lock on first call to acquireLock"() {
        Callable<String> action = Mock()
        FileLock lock = Mock()

        when:
        lockingManager.withCacheLock("some operation", action)

        then:
        1 * fileLockManager.lock(cacheDir, LockMode.Exclusive, "artifact file $cacheDir", "some operation") >> lock
        1 * action.call() >> {
            lockingManager.getLockHolder(cacheDir).acquireLock()
            lockingManager.getLockHolder(cacheDir).releaseLock()
        }
        1 * lock.close()
        0 * _._
    }

    def "releases file lock on last call to releaseLock"() {
        Callable<String> action = Mock()
        FileLock lock = Mock()

        when:
        lockingManager.withCacheLock("some operation", action)

        then:
        1 * fileLockManager.lock(cacheDir, LockMode.Exclusive, "artifact file $cacheDir", "some operation") >> lock
        1 * action.call() >> {
            lockingManager.getLockHolder(cacheDir).acquireLock()
            lockingManager.getLockHolder(cacheDir).acquireLock()
            lockingManager.getLockHolder(cacheDir).releaseLock()
            lockingManager.getLockHolder(cacheDir).releaseLock()
        }
        1 * lock.close()
        0 * _._
    }

    def "releases all locks when action completes"() {
        Callable<String> action = Mock()
        FileLock lock = Mock()

        when:
        lockingManager.withCacheLock("some operation", action)

        then:
        IllegalStateException e = thrown()
        e.message == 'Some artifact file locks were not released.'
        
        and:
        1 * fileLockManager.lock(cacheDir, LockMode.Exclusive, "artifact file $cacheDir", "some operation") >> lock
        1 * action.call() >> {
            lockingManager.getLockHolder(cacheDir).acquireLock()
            lockingManager.getLockHolder(cacheDir).acquireLock()
        }
        1 * lock.close()
        0 * _._
    }

    def "cannot lock file when artifact cache is not locked"() {
        given:
        def lockHolder = lockingManager.getLockHolder(tmpDir.file("artifact"))

        when:
        lockHolder.acquireLock()

        then:
        thrown(IllegalStateException)
    }

    def "cannot release lock when already released"() {
        Callable<String> action = Mock()
        FileLock lock = Mock()

        when:
        lockingManager.withCacheLock("some operation", action)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot release artifact file lock, as the file is not locked.'

        and:
        1 * fileLockManager.lock(cacheDir, LockMode.Exclusive, "artifact file $cacheDir", "some operation") >> lock
        1 * action.call() >> {
            lockingManager.getLockHolder(cacheDir).acquireLock()
            lockingManager.getLockHolder(cacheDir).releaseLock()
            lockingManager.getLockHolder(cacheDir).releaseLock()
        }
        1 * lock.close()
        0 * _._
    }
    
    def "cannot release lock when not locked"() {
        Callable<String> action = Mock()

        when:
        lockingManager.withCacheLock("some operation", action)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot release artifact file lock, as the file is not locked.'

        and:
        1 * action.call() >> {
            lockingManager.getLockHolder(cacheDir).releaseLock()
        }
        0 * _._
    }

     def "does not lock metadata file until metadata file lock is used"() {
        Callable<String> action = Mock()
        FileLock lock = Mock()

        when:
        lockingManager.withCacheLock("some operation", action)

        then:
        1 * action.call() >> {
            lockingManager.getCacheMetadataFileLock(cacheDir)
        }
        0 * _._

        when:
        lockingManager.withCacheLock("use metadata file", action)

        then:
        1 * fileLockManager.lock(cacheDir, LockMode.Exclusive, "metadata file ${cacheDir.name}", "use metadata file") >> lock
        1 * lock.writeToFile(_)
        1 * action.call() >> {
            FileLock metadataLock = lockingManager.getCacheMetadataFileLock(cacheDir)
            metadataLock.writeToFile(Mock(Runnable))
        }
        1 * lock.close()
        0 * _._
    }

    def "locks metadata file once for all uses of metadata file lock"() {
        Callable<String> action = Mock()
        FileLock lock = Mock()

        when:
        lockingManager.withCacheLock("use metadata file", action)

        then:
        1 * fileLockManager.lock(cacheDir, LockMode.Exclusive, "metadata file ${cacheDir.name}", "use metadata file") >> lock
        2 * lock.writeToFile(_)
        2 * lock.readFromFile(_)
        1 * action.call() >> {
            FileLock metadataLock = lockingManager.getCacheMetadataFileLock(cacheDir)
            metadataLock.writeToFile(Mock(Runnable))
            metadataLock.writeToFile(Mock(Runnable))
            metadataLock.readFromFile(Mock(Callable))
            metadataLock.readFromFile(Mock(Callable))
        }
        1 * lock.close()
        0 * _._
    }

    def "can create metadata lock before cache is locked"() {
        Callable<String> action = Mock()
        FileLock lock = Mock()

        when:
        lockingManager.getCacheMetadataFileLock(new File(cacheDir, "metadata"))

        then:
        0 * _._

        when:
        lockingManager.withCacheLock("use metadata file", action)

        then:
        1 * fileLockManager.lock(cacheDir, LockMode.Exclusive, "metadata file ${cacheDir.name}", "use metadata file") >> lock
        1 * lock.writeToFile(_)
        1 * action.call() >> {
            FileLock metadataLock = lockingManager.getCacheMetadataFileLock(cacheDir)
            metadataLock.writeToFile(Mock(Runnable))
        }
        1 * lock.close()
        0 * _._
    }

    def "can reuse metadata lock with different cache locks"() {
        Callable<String> action = Mock()
        FileLock lock = Mock()

        when:
        lockingManager.getCacheMetadataFileLock(new File(cacheDir, "metadata"))
        lockingManager.withCacheLock("use metadata file", action)

        then:
        1 * fileLockManager.lock(cacheDir, LockMode.Exclusive, "metadata file ${cacheDir.name}", "use metadata file") >> lock
        1 * lock.writeToFile(_)
        1 * action.call() >> {
            FileLock metadataLock = lockingManager.getCacheMetadataFileLock(cacheDir)
            metadataLock.writeToFile(Mock(Runnable))
        }
        1 * lock.close()
        0 * _._

        when:
        lockingManager.withCacheLock("use metadata file", action)

        then:
        1 * fileLockManager.lock(cacheDir, LockMode.Exclusive, "metadata file ${cacheDir.name}", "use metadata file") >> lock
        1 * lock.writeToFile(_)
        1 * action.call() >> {
            FileLock metadataLock = lockingManager.getCacheMetadataFileLock(cacheDir)
            metadataLock.writeToFile(Mock(Runnable))
        }
        1 * lock.close()
        0 * _._
    }

}
