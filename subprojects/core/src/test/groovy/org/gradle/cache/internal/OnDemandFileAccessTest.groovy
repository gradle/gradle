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

import org.gradle.api.internal.Factory
import org.gradle.cache.internal.FileLockManager.LockMode
import spock.lang.Specification

class OnDemandFileAccessTest extends Specification {
    final FileLockManager manager = Mock()
    final FileLock targetLock = Mock()
    final File file = new File("some-target-file")
    final OnDemandFileAccess lock = new OnDemandFileAccess(file, "some-lock", manager)

    def "acquires shared lock to read file"() {
        def action = {} as Factory

        when:
        lock.readFromFile(action)

        then:
        1 * manager.lock(file, LockMode.Shared, "some-lock") >> targetLock
        1 * targetLock.readFromFile(action)
        1 * targetLock.close()
        0 * targetLock._
    }

    def "acquires exclusive lock to write to file"() {
        def action = {} as Runnable

        when:
        lock.writeToFile(action)

        then:
        1 * manager.lock(file, LockMode.Exclusive, "some-lock") >> targetLock
        1 * targetLock.writeToFile(action)
        1 * targetLock.close()
        0 * targetLock._
    }
}
