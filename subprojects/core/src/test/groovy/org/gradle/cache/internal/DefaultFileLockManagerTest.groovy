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

import org.gradle.cache.internal.FileLockManager.LockMode
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

/**
 * @author: Szczepan Faber, created at: 8/30/11
 */
class DefaultFileLockManagerTest extends Specification {
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder()
    def manager = new DefaultFileLockManager()

    def "can lock a file"() {
        when:
        def lock = manager.lock(tmpDir.createFile("file.txt"), LockMode.Shared, "lock")

        then:
        lock.isLockFile(tmpDir.createFile("file.txt.lock"))
    }

    def "can lock a directory"() {
        when:
        def lock = manager.lock(tmpDir.createDir("some-dir"), LockMode.Shared, "lock")

        then:
        lock.isLockFile(tmpDir.createFile("some-dir/some-dir.lock"))
    }

    def "can lock a file once it has been released"() {
        given:
        def fileLock = lock(FileLockManager.LockMode.Exclusive);
        fileLock.close()

        when:
        lock(FileLockManager.LockMode.Exclusive);

        then:
        notThrown(RuntimeException)
    }

    def "cannot lock a file twice in single process"() {
        given:
        lock(FileLockManager.LockMode.Exclusive);

        when:
        lock(FileLockManager.LockMode.Exclusive);

        then:
        thrown(IllegalStateException)
    }

    def "cannot lock twice in single process for mixed modes"() {
        given:
        lock(FileLockManager.LockMode.Exclusive);

        when:
        lock(FileLockManager.LockMode.Shared);

        then:
        thrown(IllegalStateException)
    }

    def "cannot lock twice in single process for shared mode"() {
        given:
        lock(FileLockManager.LockMode.Shared);

        when:
        lock(FileLockManager.LockMode.Shared);

        then:
        thrown(IllegalStateException)
    }

    private FileLock lock(LockMode lockMode) {
        return manager.lock(tmpDir.file("state.bin"), lockMode, "foo")
    }
}
