/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.cache.FileAccess
import org.gradle.cache.FileIntegrityViolationException
import org.gradle.cache.FileLock
import org.gradle.cache.FileLockManager
import org.gradle.cache.internal.filelock.LockOptionsBuilder
import org.gradle.cache.internal.locklistener.NoOpFileLockContentionHandler

abstract class DefaultFileLockManagerTestHelper {

    private static class AnException extends RuntimeException {}

    static FileAccess createOnDemandFileLock(File file) {
        new OnDemandFileAccess(file, "test", DefaultFileLockManagerTestHelper.createDefaultFileLockManager())
    }

    static void unlockUncleanly(File target) {
        def lock = createDefaultFileLock(target)
        try {
            lock.writeFile {
                throw new AnException()
            }
        } catch (AnException e) {
            lock.close()
        }
        lock = createDefaultFileLock(target)
        try {
            assert !lock.unlockedCleanly
        } finally {
            lock.close()
        }

    }

    static DefaultFileLockManager createDefaultFileLockManager() {
        new DefaultFileLockManager(new ProcessMetaDataProvider() {
            String getProcessIdentifier() {
                return "pid"
            }

            String getProcessDisplayName() {
                return "process"
            }
        }, new NoOpFileLockContentionHandler())
    }

    static DefaultFileLockManager createDefaultFileLockManager(int timeout) {
        new DefaultFileLockManager(new ProcessMetaDataProvider() {
            String getProcessIdentifier() {
                return "pid"
            }

            String getProcessDisplayName() {
                return "process"
            }
        }, timeout, new NoOpFileLockContentionHandler())
    }

    static FileLock createDefaultFileLock(File file, FileLockManager.LockMode mode = FileLockManager.LockMode.Exclusive, DefaultFileLockManager manager = createDefaultFileLockManager()) {
        manager.lock(file, new LockOptionsBuilder(mode), "test lock")
    }

    static File getLockFile(File target) {
        new File(target.absolutePath + ".lock")
    }

    static boolean isIntegrityViolated(File file) {
        try {
            createOnDemandFileLock(file).readFile { }
            false
        } catch (FileIntegrityViolationException e) {
            true
        }
    }
}
