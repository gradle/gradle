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
     * <p>Requests that the daemon stop, but wait until the daemon is idle. The stop will happen asynchronously, and this method does not block.
     *
     * <p>The daemon will stop accepting new work, so that subsequent calls to {@link #runCommand} will failing with {@link DaemonUnavailableException}.
     */
    void requestStop();

    /**
     * Requests a forceful stops of the daemon. Does not wait until the daemon is idle to begin stopping. The stop will happen asynchronously, and this method does not block.
     *
     * <p>If any long running command is currently running, the operation's abandoned command handler will be executed.</p>
     *
     * <p>The daemon will stop accepting new work, so that subsequent calls to {@link #runCommand} will failing with {@link DaemonUnavailableException}.
     */
    void requestForcefulStop();

    /**
     * Runs the given long running command. No more than 1 command may be running at any given time.
     *
     * @param command The command to run
     * @param commandDisplayName The command's display name, used for logging and error messages.
     * @param onCommandAbandoned An action to run with a forceful stop is requested using {@link #requestForcefulStop()}, to notify the caller that the operation is being abandoned.
     *
     * @throws DaemonUnavailableException If this daemon is currently executing another command or a stop has been requested.
     */
    void runCommand(Runnable command, String commandDisplayName, Runnable onCommandAbandoned) throws DaemonUnavailableException;
}
