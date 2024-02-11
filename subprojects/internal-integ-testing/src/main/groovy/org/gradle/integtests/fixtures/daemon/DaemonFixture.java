/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.fixtures.daemon;

import org.gradle.launcher.daemon.context.DaemonContext;

import java.io.File;

public interface DaemonFixture {
    /**
     * Returns the context information of this daemon.
     */
    DaemonContext getContext();

    /**
     * Returns the log for this daemon.
     */
    String getLog();

    /**
     * Returns the log file for this daemon.
     */
    File getLogFile();

    /**
     * Returns whether the log file contains a given String.
     *
     * Works without reading the whole log file into memory.
     */
    boolean logContains(String searchString);

    /**
     * Returns whether the log file contains a given String, starting from line `fromLine`
     *
     * The first line in the file is the line 0.
     *
     * Works without reading the whole log file into memory.
     */
    boolean logContains(long fromLine, String searchString);

    /**
     * Returns the number of lines in the daemon log.
     *
     * Works without reading the whole log file into memory.
     */
    long getLogLineCount();

    /**
     * Returns the TCP port used by this daemon.
     */
    int getPort();

    /**
     * Forcefully kills this daemon and all child processes.
     */
    void kill();

    /**
     * Forcefully kills this daemon, but not child processes.
     */
    void killDaemonOnly();

    /**
     * Changes the authentication token for this daemon in the registry, so that client will see a different token to that expected by this daemon
     */
    void changeTokenVisibleToClient();

    void assertRegistryNotWorldReadable();

    /**
     * Asserts that this daemon becomes idle within a short timeout. Blocks until this has happened.
     */
    DaemonFixture becomesIdle();

    /**
     * Asserts that this daemon stops and is no longer visible to any clients within a short timeout. Blocks until this has happened.
     */
    DaemonFixture stops();

    /**
     * Asserts that this daemon is currently idle.
     */
    void assertIdle();

    /**
     * Asserts that this daemon is currently busy.
     */
    void assertBusy();

    /**
     * Asserts that this daemon is in a canceled state.
     */
    void assertCanceled();

    /**
     * Asserts that this daemon becomes canceled within a short timeout. Blocks until this has happened.
     */
    DaemonFixture becomesCanceled();

    /**
     * Asserts that this daemon has stopped and is no longer visible to any clients.
     */
    void assertStopped();
}
