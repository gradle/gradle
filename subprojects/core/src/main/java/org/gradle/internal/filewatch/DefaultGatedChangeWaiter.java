/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.filewatch;

import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.ContinuousExecutionGate;

class DefaultGatedChangeWaiter implements FileSystemChangeWaiter {
    private final FileSystemChangeWaiter delegate;
    private final BuildCancellationToken cancellationToken;
    private final ContinuousExecutionGate continuousExecutionGate;

    DefaultGatedChangeWaiter(FileSystemChangeWaiter delegate, BuildCancellationToken cancellationToken, ContinuousExecutionGate continuousExecutionGate) {
        this.delegate = delegate;
        this.cancellationToken = cancellationToken;
        this.continuousExecutionGate = continuousExecutionGate;
    }

    @Override
    public void watch(FileSystemSubset fileSystemSubset) {
        delegate.watch(fileSystemSubset);
    }

    @Override
    public void wait(Runnable notifier, FileWatcherEventListener eventListener) {
        delegate.wait(notifier, eventListener);
        if (!cancellationToken.isCancellationRequested()) {
            continuousExecutionGate.waitForOpen();
        }
    }

    @Override
    public boolean isWatching() {
        return delegate.isWatching();
    }

    @Override
    public void stop() {
        delegate.stop();
    }
}
