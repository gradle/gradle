/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.launcher.daemon.logging;

public abstract class DaemonMessages {
    public final static String PROCESS_STARTED = "Daemon server started.";
    public final static String ABOUT_TO_CLOSE_STREAMS = "Daemon started. About to close the streams. Daemon details: ";
    public final static String STARTED_RELAYING_LOGS = "The client will now receive all logging from the daemon (pid: ";
    public final static String UNABLE_TO_START_DAEMON = "Unable to start the daemon process.";
    public final static String STARTED_EXECUTING_COMMAND = "Starting executing command: ";
    public final static String FINISHED_EXECUTING_COMMAND = "Finishing executing command: ";
    public final static String FINISHED_BUILD = "The daemon has finished executing the build.";
    public final static String NO_DAEMONS_RUNNING = "No Gradle daemons are running.";
    public final static String ABOUT_TO_START_RELAYING_LOGS = "About to start relaying all logs to the client via the connection.";
    public final static String DAEMON_VM_SHUTTING_DOWN = "Daemon vm is shutting down... The daemon has exited normally or was terminated in response to a user interrupt.";
    public final static String REMOVING_PRESENCE_DUE_TO_STOP = "Stop requested. Daemon is removing its presence from the registry...";
    public static final String ADVERTISING_DAEMON = "Advertising the daemon address to the clients: ";
    public static final String DAEMON_IDLE = "Daemon is idle, sleeping until state change or idle timeout at ";
    public static final String DAEMON_BUSY = "Daemon is busy, sleeping until state changes.";
    public static final String REMOVING_DAEMON_ADDRESS_ON_FAILURE = "Removing daemon from the registry due to communication failure. Daemon information: ";
}
