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
package org.gradle.cache.internal;

import org.gradle.util.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.Callable;

public class DefaultFileLockManager implements FileLockManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFileLockManager.class);
    private static final byte LOCK_PROTOCOL = 1;
    private static final int LOCK_TIMEOUT = 15000;

    public FileLock lock(File target, LockMode mode, String displayName) {
        return new DefaultFileLock(target, mode, displayName);
    }

    private static class DefaultFileLock implements FileLock {
        private final File lockFile;
        private final RandomAccessFile lockFileAccess;
        private final LockMode mode;
        private final String displayName;
        private java.nio.channels.FileLock lock;

        public DefaultFileLock(File target, LockMode mode, String displayName) {
            this.mode = mode;
            this.displayName = displayName;
            lockFile = new File(target.getParentFile(), target.getName() + ".lock");
            try {
                lockFile.getParentFile().mkdirs();
                lockFile.createNewFile();
                lockFileAccess = new RandomAccessFile(lockFile, "rw");
                try {
                    lock = lock(mode);
                } catch (Throwable t) {
                    // Also releases any locks
                    lockFileAccess.close();
                    throw t;
                }
            } catch (Throwable throwable) {
                throw UncheckedException.asUncheckedException(throwable);
            }
        }

        public boolean isLockFile(File file) {
            return file.equals(lockFile);
        }

        public boolean getUnlockedCleanly() {
            return readFromFile(new Callable<Boolean>() {
                public Boolean call() throws Exception {
                    lockFileAccess.seek(0);
                    try {
                        if (lockFileAccess.readByte() != LOCK_PROTOCOL) {
                            throw new IllegalStateException(String.format("Unexpected lock protocol found in lock file '%s' for %s.", lockFile, displayName));
                        }
                        if (!lockFileAccess.readBoolean()) {
                            // Process has crashed while updating target file
                            return false;
                        }
                    } catch (EOFException e) {
                        // Process has crashed writing to lock file
                        return false;
                    }
                    return true;
                }
            });
        }

        public <T> T readFromFile(Callable<T> action) throws LockTimeoutException {
            try {
                return action.call();
            } catch (Exception e) {
                throw UncheckedException.asUncheckedException(e);
            }
        }

        public void writeToFile(Runnable action) {
            try {
                // TODO - need to escalate without releasing lock
                java.nio.channels.FileLock updateLock = null;
                if (mode != LockMode.Exclusive) {
                    lock.release();
                    lock = null;
                    updateLock = lock(LockMode.Exclusive);
                }
                try {
                    markDirty();
                    action.run();
                    markClean();
                } finally {
                    if (mode != LockMode.Exclusive) {
                        updateLock.release();
                        lock = lock(mode);
                    }
                }
            } catch (IOException e) {
                throw UncheckedException.asUncheckedException(e);
            } catch (InterruptedException e) {
                throw UncheckedException.asUncheckedException(e);
            }
        }

        private void markClean() throws IOException {
            lockFileAccess.seek(0);
            lockFileAccess.writeByte(LOCK_PROTOCOL);
            lockFileAccess.writeBoolean(true);
        }

        private void markDirty() throws IOException {
            lockFileAccess.seek(0);
            lockFileAccess.writeByte(LOCK_PROTOCOL);
            lockFileAccess.writeBoolean(false);
        }

        public void close() {
            try {
                LOGGER.debug("Releasing lock on {}.", displayName);
                // Also releases any locks
                lockFileAccess.close();
            } catch (IOException e) {
                throw UncheckedException.asUncheckedException(e);
            }
        }

        private java.nio.channels.FileLock lock(FileLockManager.LockMode lockMode) throws IOException, InterruptedException {
            LOGGER.debug("Waiting to acquire {} lock on {}.", lockMode, displayName);
            long timeout = System.currentTimeMillis() + LOCK_TIMEOUT;
            do {
                java.nio.channels.FileLock fileLock = lockFileAccess.getChannel().tryLock(0, Long.MAX_VALUE, lockMode == LockMode.Shared);
                if (fileLock != null) {
                    LOGGER.debug("Lock acquired.");
                    return fileLock;
                }
                Thread.sleep(200L);
            } while (System.currentTimeMillis() < timeout);
            throw new LockTimeoutException(String.format("Timeout waiting to lock %s. It is currently in use by another Gradle instance.", displayName));
        }
    }
}
