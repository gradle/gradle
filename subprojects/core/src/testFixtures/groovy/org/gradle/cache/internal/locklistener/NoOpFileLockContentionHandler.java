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

package org.gradle.cache.internal.locklistener;

import org.gradle.cache.FileLockReleasedSignal;

import java.util.function.Consumer;

public class NoOpFileLockContentionHandler implements FileLockContentionHandler {

    @Override
    public void start(long lockId, Consumer<FileLockReleasedSignal> whenContended) {}

    @Override
    public void stop(long lockId) {}

    @Override
    public int reservePort() {
        return -1;
    }

    @Override
    public boolean maybePingOwner(int port, long lockId, String displayName, long timeElapsed, FileLockReleasedSignal signal) {
        return false;
    }

    @Override
    public boolean isRunning() {
        return true;
    }
}
