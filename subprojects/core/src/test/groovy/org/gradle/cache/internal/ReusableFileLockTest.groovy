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

import java.util.concurrent.Callable
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

class ReusableFileLockTest extends Specification {
    @Rule final TemporaryFolder tmpDir = new TemporaryFolder()
    final FileLockManager fileLockManager = Mock()
    final File cacheFile = tmpDir.file("cache-file")
    final ReusableFileLock lock = new ReusableFileLock(cacheFile, "display name", fileLockManager)

    def "actions fail when not manually locked"() {
        Runnable writeAction = Mock()
        Callable<String> readAction = Mock()

        when:
        lock.writeToFile(writeAction)

        then:
        thrown(IllegalStateException)

        when:
        lock.readFromFile(readAction)

        then:
        thrown(IllegalStateException)
    }

    def "actions fail when manually unlocked"() {
        Runnable writeAction = Mock()
        Callable<String> readAction = Mock()
        FileLock realLock = Mock()

        when:
        lock.lock()
        lock.unlock()

        then:
        1 * fileLockManager.lock(cacheFile, FileLockManager.LockMode.Exclusive, _) >> realLock
        realLock.close()

        when:
        lock.writeToFile(writeAction)

        then:
        thrown(IllegalStateException)

        when:
        lock.readFromFile(readAction)

        then:
        thrown(IllegalStateException)
    }

    def "executes actions when manually locked"() {
        Runnable writeAction = Mock()
        Callable<String> readAction = Mock()
        FileLock realLock = Mock()

        when:
        lock.lock()

        then:
        1 * fileLockManager.lock(cacheFile, FileLockManager.LockMode.Exclusive, _) >> realLock

        when:
        lock.writeToFile(writeAction)

        then:
        1 * writeAction.run()

        when:
        lock.readFromFile(readAction)

        then:
        1 * readAction.call()
    }
}
