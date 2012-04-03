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

package org.gradle.launcher.daemon.server.exec;

public interface DaemonStateControl {
    /**
     * Perform a stop, but wait until the daemon is idle to cut any open connections.
     *
     * The daemon will be removed from the registry upon calling this regardless of whether it is busy or not.
     * If it is idle, this method will block until the daemon fully stops.
     *
     * If the daemon is busy, this method will return after removing the daemon from the registry but before the
     * daemon is fully stopped. In this case, the daemon will stop as soon as {@link #onFinishCommand()} is called.
     */
    void stopAsSoonAsIdle();

    /**
     * @return returns false if the daemon was already requested to stop
     */
    boolean requestStop();

    /**
     * Called when the execution of a command begins.
     * <p>
     * If the daemon is busy (i.e. already executing a command), this method will return the existing
     * execution which the caller should be prepared for without considering the given execution to be in progress.
     * If the daemon is idle the return value will be {@code null} and the given execution will be considered in progress.
     */
    DaemonCommandExecution onStartCommand(DaemonCommandExecution execution);

    /**
     * Called when the execution of a command is complete (or at least the daemon is available for new commands).
     * <p>
     * If the daemon is currently idle, this method will return {@code null}. If it is busy, it will return what was the
     * current execution which will considered to be complete (putting the daemon back in idle state).
     * <p>
     * If {@link #stopAsSoonAsIdle()} was previously called, this method will block while the daemon stops.
     */
    DaemonCommandExecution onFinishCommand();
}
