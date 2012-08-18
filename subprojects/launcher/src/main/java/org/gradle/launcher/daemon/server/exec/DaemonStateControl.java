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
     * daemon is fully stopped. In this case, the daemon will stop as soon as {@link #runCommand(Runnable, String)} has completed.
     */
    void stopAsSoonAsIdle();

    /**
     * @return returns false if the daemon was already requested to stop
     */
    boolean requestStop();

    /**
     * Runs the given command. At the completion of the command, if {@link #stopAsSoonAsIdle()} was previously called, this method will block while the daemon stops.
     *
     * @throws DaemonBusyException If this daemon is currently executing another command.
     */
    void runCommand(Runnable command, String commandDisplayName) throws DaemonBusyException;
}
