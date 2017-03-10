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
     * Returns the TCP port used by this daemon.
     */
    int getPort();

    /**
     * Forcefully kills this daemon.
     */
    void kill();

    /**
     * Changes the authentication token for this daemon in the registry, so that client will see a different token to that expected by this daemon
     */
    void changeTokenVisibleToClient();

    void assertRegistryNotWorldReadable();

    /**
     * Asserts that this daemon becomes idle within a short timeout. Blocks until this has happened.
     */
    void becomesIdle();

    /**
     * Asserts that this daemon stops and is no longer visible to any clients within a short timeout. Blocks until this has happened.
     */
    void stops();

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
    void becomesCanceled();

    /**
     * Asserts that this daemon has stopped and is no longer visible to any clients.
     */
    void assertStopped();
}
