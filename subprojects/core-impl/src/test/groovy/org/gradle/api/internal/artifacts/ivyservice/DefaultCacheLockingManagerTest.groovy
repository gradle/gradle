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
import spock.lang.Specification
import org.gradle.util.TemporaryFolder
import org.junit.Rule

class DefaultCacheLockingManagerTest extends Specification {
    @Rule final TemporaryFolder tmpDir = new TemporaryFolder()
    final FileLockManager fileLockManager = Mock()
    final File cacheDir = tmpDir.file("cache-dir")
    final ArtifactCacheMetaData cacheMetaData = Mock()
    final DefaultCacheLockingManager lockingManager = new DefaultCacheLockingManager(fileLockManager, cacheMetaData)

    def "locks artifact cache and executes action"() {
        Callable<String> action = Mock()
        FileLock lock = Mock()

        when:
        def result = lockingManager.withCacheLock(action)

        then:
        result == 'result'

        and:
        1 * fileLockManager.lock(cacheDir, LockMode.Exclusive, "artifact cache") >> lock
        1 * action.call() >> 'result'
        1 * lock.close()
        _ * cacheMetaData.cacheDir >> cacheDir
        0 * _._
    }

    def "releases artifact cache lock when action fails"() {
        Callable<String> action = Mock()
        FileLock lock = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        lockingManager.withCacheLock(action)

        then:
        RuntimeException e = thrown()
        e == failure

        and:
        1 * fileLockManager.lock(cacheDir, LockMode.Exclusive, "artifact cache") >> lock
        1 * action.call() >> { throw failure }
        1 * lock.close()
        _ * cacheMetaData.cacheDir >> cacheDir
        0 * _._
    }

    def "can lock file while artifact cache is locked"() {
        Callable<String> action = Mock()
        FileLock lock = Mock()

        given:

        when:
        lockingManager.withCacheLock(action)

        then:
        _ * fileLockManager.lock(_, _, _) >> lock
        1 * action.call() >> {
            def lockHolder = lockingManager.getLockHolder(tmpDir.file("artifact"))
            assert lockHolder.acquireLock()
            lockHolder.releaseLock()
        }
    }

    def "cannot lock file when artifact cache is not locked"() {
        given:
        def lockHolder = lockingManager.getLockHolder(tmpDir.file("artifact"))

        when:
        lockHolder.acquireLock()

        then:
        thrown(IllegalStateException)
    }
}
