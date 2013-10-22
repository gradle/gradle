/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.cache.internal.filelock;

import org.gradle.cache.internal.FileLockManager;

public class LockOptionsBuilder implements LockOptions {

    private FileLockManager.LockMode mode;
    private boolean simple;

    private LockOptionsBuilder(FileLockManager.LockMode mode, boolean simple) {
        this.mode = mode;
        this.simple = simple;
    }

    public static LockOptionsBuilder mode(FileLockManager.LockMode lockMode) {
        return new LockOptionsBuilder(lockMode, false);
    }

    public LockOptionsBuilder simple() {
        simple = true;
        return this;
    }

    public FileLockManager.LockMode getMode() {
        return mode;
    }

    public LockStateSerializer getLockStateSerializer() {
        return simple? new Version1LockStateSerializer() : new DefaultLockStateSerializer();
    }

    public LockOptions withMode(FileLockManager.LockMode mode) {
        return mode(mode).simple(simple);
    }

    private LockOptions simple(boolean simple) {
        this.simple = simple;
        return this;
    }

    @Override
    public String toString() {
        return mode + " (simple=" + simple + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LockOptionsBuilder)) {
            return false;
        }

        LockOptionsBuilder that = (LockOptionsBuilder) o;

        if (simple != that.simple) {
            return false;
        }
        if (mode != that.mode) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = mode.hashCode();
        result = 31 * result + (simple ? 1 : 0);
        return result;
    }
}
