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

import java.nio.channels.OverlappingFileLockException
import org.gradle.cache.internal.FileLockManager.LockMode
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Specification

/**
 * @author: Szczepan Faber, created at: 8/30/11
 */
@Ignore //it seems to work differently on our linux box (due to java 1.5?)
class DefaultFileLockManagerTest extends Specification {
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder()
    def manager = new DefaultFileLockManager()

    def "cannot lock twice in single process"() {
        when:
        lock(FileLockManager.LockMode.Exclusive);
        lock(FileLockManager.LockMode.Exclusive);

        then:
        thrown(OverlappingFileLockException)
    }

    def "cannot lock twice in single process for mixed modes"() {
        when:
        lock(FileLockManager.LockMode.Exclusive);
        lock(FileLockManager.LockMode.Shared);

        then:
        thrown(OverlappingFileLockException)
    }

    def "cannot lock twice in single process for shared mode"() {
        when:
        lock(FileLockManager.LockMode.Shared);
        lock(FileLockManager.LockMode.Shared);

        then:
        thrown(OverlappingFileLockException)
    }

    private FileLock lock(LockMode lockMode) {
        return manager.lock(tmpDir.file("state.bin"), lockMode, "foo")
    }
}
