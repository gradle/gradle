/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.filewatch.jdk7;

import org.gradle.internal.filewatch.FileWatcher;

/**
 * Class for handling the notification of changes
 *
 * Quiet period handling is currently implemented in this class.
 *
 * {@link FileWatcherExecutor} will call the addPendingChange method for each change it notices.
 * At the end of the polling loop, it will call the handlePendingChanges method.
 * handlePendingChanges method will get called every time the poll times out in the watch loop of FileWatcherExecutor.
 *
 */
class FileWatcherChangesNotifier {
    static final long QUIET_PERIOD_MILLIS = 1000L;
    static final long QUIET_PERIOD_TIMEOUT_MILLIS = 10000L;
    private Runnable callback;
    private long lastEventReceivedMillis;
    private long quietPeriodWaitingStartedMillis;
    private boolean pendingNotification;
    private FileWatcher fileWatcher;

    public FileWatcherChangesNotifier(FileWatcher fileWatcher, Runnable callback) {
        this.fileWatcher = fileWatcher;
        this.callback = callback;
    }

    public void addPendingChange() {
        pendingNotification = true;
    }

    public void handlePendingChanges() {
        if (pendingNotification) {
            if(quietPeriodWaitingStartedMillis == -1) {
                quietPeriodWaitingStartedMillis = System.currentTimeMillis();
            }
            if(quietPeriodBeforeNotifyingHasElapsed() || quietPeriodBeforeNotifyingHasTimedOut()) {
                notifyChanged();
                pendingNotification = false;
                quietPeriodWaitingStartedMillis = -1;
            }
        }
    }

    public void reset() {
        pendingNotification = false;
        lastEventReceivedMillis = -1L;
        quietPeriodWaitingStartedMillis = -1L;
    }

    public void eventReceived() {
        lastEventReceivedMillis = System.currentTimeMillis();
    }

    protected void notifyChanged() {
        callback.run();
    }

    protected boolean quietPeriodBeforeNotifyingHasTimedOut() {
        return System.currentTimeMillis() - quietPeriodWaitingStartedMillis > QUIET_PERIOD_TIMEOUT_MILLIS;
    }

    protected boolean quietPeriodBeforeNotifyingHasElapsed() {
        return QUIET_PERIOD_MILLIS <= 0 || System.currentTimeMillis() - lastEventReceivedMillis > QUIET_PERIOD_MILLIS;
    }
}
