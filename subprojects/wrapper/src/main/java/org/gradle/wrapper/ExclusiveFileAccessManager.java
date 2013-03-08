/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.wrapper;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.concurrent.Callable;

public class ExclusiveFileAccessManager {

    public static final String LOCK_FILE_SUFFIX = ".lck";

    private final int timeoutMs;
    private final int pollIntervalMs;
    private final Locker locker;

    static interface Locker {
        FileLock tryLock(FileChannel channel) throws IOException;
    }

    static class DefaultLocker implements Locker {
        public FileLock tryLock(FileChannel channel) throws IOException {
            return channel.tryLock();
        }
    }

    ExclusiveFileAccessManager(int timeoutMs, int pollIntervalMs, Locker locker) {
        this.timeoutMs = timeoutMs;
        this.pollIntervalMs = pollIntervalMs;
        this.locker = locker;
    }

    public ExclusiveFileAccessManager(int timeoutMs, int pollIntervalMs) {
        this(timeoutMs, pollIntervalMs, new DefaultLocker());
    }

    public <T> T access(File exclusiveFile, Callable<T> task) {
        final File lockFile = new File(exclusiveFile.getParentFile(), exclusiveFile.getName() + LOCK_FILE_SUFFIX);
        RandomAccessFile randomAccessFile = null;
        FileChannel channel = null;
        try {

            long startAt = System.currentTimeMillis();
            FileLock lock = null;

            while (lock == null && System.currentTimeMillis() < startAt + timeoutMs) {
                try {
                    randomAccessFile = new RandomAccessFile(lockFile, "rw");
                    channel = randomAccessFile.getChannel();
                    lock = locker.tryLock(channel);
                } catch (OverlappingFileLockException ignore) {
                    // this JVM already has a lock on this file
                }

                if (lock == null) {
                    maybeCloseQuietly(channel);
                    maybeCloseQuietly(randomAccessFile);
                    Thread.sleep(pollIntervalMs);
                }
            }

            if (lock == null) {
                throw new RuntimeException("Timeout of " + timeoutMs + " reached waiting for exclusive access to file: " + exclusiveFile.getAbsolutePath());
            }

            try {
                return task.call();
            } finally {
                lockFile.delete();
                lock.release();
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        } finally {
            maybeCloseQuietly(channel);
            maybeCloseQuietly(randomAccessFile);
        }
    }

    private static void maybeCloseQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignore) {
                //
            }
        }
    }
}
