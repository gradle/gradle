/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.launcher.daemon.management.internal;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Checks on and signals daemon processes.
 *
 * <p>This is the last resort, used when a daemon does not answer on its socket. A daemon that cannot be
 * talked to cannot be stopped politely, and leaving it running because it stopped listening would make
 * stopping daemons unreliable in exactly the case where a user most wants it.
 *
 * <p>A process is only signalled once it has been confirmed to be a Gradle daemon. The process id comes
 * from a file, so it describes the past rather than the present, and anything that cannot be confirmed
 * is left alone.
 *
 * <p>Signals are sent by running the platform's own tool rather than through {@code ProcessHandle},
 * which arrived in Java 9. This code is part of the Gradle client and still compiles against Java 8.
 */
public class ProcessController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessController.class);
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    private static final int SIGNAL_TIMEOUT_MILLIS = 5000;
    private static final int DEATH_POLL_MILLIS = 100;

    /**
     * The entry point every Gradle daemon is launched with, and has been since well before the oldest
     * version this library reads. Build workers carry a different one, so matching on it does not
     * mistake a worker for the daemon that owns it.
     */
    private static final String DAEMON_MAIN_CLASS = "org.gradle.launcher.daemon.bootstrap.GradleDaemon";

    public boolean isAlive(long pid) {
        if (!isAddressable(pid)) {
            return false;
        }
        if (WINDOWS) {
            String output = runAndCapture(Arrays.asList("tasklist", "/FI", "PID eq " + pid, "/NH"));
            return output != null && output.contains(String.valueOf(pid));
        }
        return run(Arrays.asList("kill", "-0", String.valueOf(pid)));
    }

    /**
     * Asks the process to exit, then insists if it does not.
     *
     * @param graceDeadlineMillis how long to wait for a polite exit before forcing it
     * @return whether the process is gone
     */
    public boolean terminate(long pid, long graceDeadlineMillis) {
        if (!isAddressable(pid)) {
            LOGGER.debug("Refusing to signal {}, which is not a process id.", pid);
            return false;
        }
        if (!isAlive(pid)) {
            return true;
        }
        if (!isDaemonProcess(pid)) {
            LOGGER.debug("Refusing to signal {}, which is running but is not a Gradle daemon.", pid);
            return false;
        }
        if (WINDOWS) {
            run(Arrays.asList("taskkill", "/PID", String.valueOf(pid)));
        } else {
            run(Arrays.asList("kill", "-TERM", String.valueOf(pid)));
        }
        if (awaitDeath(pid, graceDeadlineMillis)) {
            return true;
        }
        LOGGER.debug("Daemon process {} did not exit after being asked to, forcing it.", pid);
        if (WINDOWS) {
            run(Arrays.asList("taskkill", "/F", "/PID", String.valueOf(pid)));
        } else {
            run(Arrays.asList("kill", "-KILL", String.valueOf(pid)));
        }
        return awaitDeath(pid, graceDeadlineMillis);
    }

    private boolean awaitDeath(long pid, long deadlineMillis) {
        long giveUpAt = System.currentTimeMillis() + deadlineMillis;
        while (System.currentTimeMillis() < giveUpAt) {
            if (!isAlive(pid)) {
                return true;
            }
            try {
                Thread.sleep(DEATH_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return !isAlive(pid);
            }
        }
        return !isAlive(pid);
    }

    /**
     * Whether this number names a single process that may be signalled.
     *
     * <p>Process ids arrive from files that other Gradle versions wrote and that this code reads without
     * a lock, so a value can be anything. It matters because {@code kill} reads numbers below 1 as
     * something other than one process: 0 means the caller's whole process group, and -1 means every
     * process the user is allowed to signal. Sending a signal to either would be catastrophic and is
     * never what a stale or misread entry was asking for.
     */
    private static boolean isAddressable(long pid) {
        return pid > 0;
    }

    /**
     * Whether the process with this id is a Gradle daemon.
     *
     * <p>Being addressable is not enough to make a process the right target. A process id read out of a
     * file says what was true when the file was written, and process ids are recycled, so a stale entry
     * can name a process that has nothing to do with Gradle. A real Gradle user home accumulates these:
     * a daemon killed outright never runs the shutdown hook that would withdraw its entry, and the entry
     * outlives the process by years. Signalling on the strength of such an entry would terminate
     * whatever inherited the number.
     *
     * <p>This answers no when the command line cannot be read at all. The cost of that is a wedged
     * daemon that has to be killed by hand; the cost of the other default is killing something that was
     * never asked about, so the uncertain case is the one to refuse.
     */
    private boolean isDaemonProcess(long pid) {
        String commandLine = readCommandLine(pid);
        if (commandLine == null) {
            LOGGER.debug("Could not read the command line of process {}, so it cannot be confirmed as a daemon.", pid);
            return false;
        }
        return commandLine.contains(DAEMON_MAIN_CLASS);
    }

    private @Nullable String readCommandLine(long pid) {
        if (!WINDOWS) {
            return runAndCapture(Arrays.asList("ps", "-p", String.valueOf(pid), "-o", "command="));
        }
        String fromWmic = runAndCapture(Arrays.asList(
            "wmic", "process", "where", "ProcessId=" + pid, "get", "CommandLine", "/format:list"));
        if (fromWmic != null) {
            return fromWmic;
        }
        // wmic is absent from recent Windows releases.
        return runAndCapture(Arrays.asList(
            "powershell", "-NoProfile", "-Command",
            "(Get-CimInstance Win32_Process -Filter 'ProcessId=" + pid + "').CommandLine"));
    }

    private boolean run(List<String> command) {
        return runAndCapture(command) != null;
    }

    private String runAndCapture(List<String> command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            StringBuilder output = new StringBuilder();
            byte[] buffer = new byte[512];
            int read;
            while ((read = process.getInputStream().read(buffer)) != -1) {
                output.append(new String(buffer, 0, read, "UTF-8"));
            }
            if (!waitFor(process)) {
                return null;
            }
            return process.exitValue() == 0 ? output.toString() : null;
        } catch (Exception e) {
            LOGGER.debug("Could not run {}.", command, e);
            return null;
        }
    }

    private static boolean waitFor(Process process) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + SIGNAL_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            try {
                process.exitValue();
                return true;
            } catch (IllegalThreadStateException stillRunning) {
                Thread.sleep(DEATH_POLL_MILLIS);
            }
        }
        process.destroy();
        return false;
    }
}
