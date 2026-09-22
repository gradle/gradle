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

package org.gradle.launcher.cli;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.daemon.management.DaemonStatus;
import org.gradle.launcher.daemon.management.ManagedDaemon;
import org.gradle.launcher.daemon.management.ManagedDaemons;
import org.gradle.launcher.daemon.management.UnsupportedDaemonVersionException;
import org.gradle.util.GradleVersion;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Carries out the daemon management commands that work across Gradle versions.
 */
public class ManagedDaemonsAction implements Runnable {

    private static final Logger LOGGER = Logging.getLogger(ManagedDaemonsAction.class);
    private static final String STATUS_FORMAT = "%1$8s %2$-10s %3$-14s %4$s";

    /**
     * What this invocation was asked to do.
     */
    public enum Verb {
        STATUS, STOP, STOP_WHEN_IDLE, CANCEL, START
    }

    private final ManagedDaemons daemons;
    private final Verb verb;
    private final boolean allVersions;
    private final @Nullable String cancelPid;
    private final @Nullable File projectDir;

    public ManagedDaemonsAction(ManagedDaemons daemons, Verb verb, boolean allVersions, @Nullable String cancelPid, @Nullable File projectDir) {
        this.daemons = daemons;
        this.verb = verb;
        this.allVersions = allVersions;
        this.cancelPid = cancelPid;
        this.projectDir = projectDir;
    }

    @Override
    public void run() {
        switch (verb) {
            case STATUS:
                reportStatus();
                break;
            case STOP:
                stop();
                break;
            case STOP_WHEN_IDLE:
                stopWhenIdle();
                break;
            case CANCEL:
                cancel();
                break;
            case START:
                start();
                break;
        }
    }

    private void reportStatus() {
        List<ManagedDaemon> found = inScope();
        if (found.isEmpty()) {
            LOGGER.quiet("No Gradle daemons are running.");
            return;
        }
        LOGGER.quiet(String.format(STATUS_FORMAT, "PID", "STATUS", "VERSION", "INFO"));
        for (ManagedDaemon daemon : found) {
            DaemonStatus status = daemon.status();
            LOGGER.quiet(String.format(STATUS_FORMAT,
                status.getPid() == null ? "?" : status.getPid(),
                status.getState(),
                daemon.getGradleVersion(),
                describe(status, daemon)));
        }
    }

    private static String describe(DaemonStatus status, ManagedDaemon daemon) {
        switch (status.getSource()) {
            case LIVE:
                return "";
            case RECORDED:
                return "state last recorded by the daemon";
            case UNREACHABLE:
            default:
                return daemon.getDiscovery() == ManagedDaemon.Discovery.INDEX
                    ? "not responding, its entry is stale"
                    : "not responding, registered by Gradle " + daemon.getGradleVersion();
        }
    }

    private void stop() {
        List<ManagedDaemon> found = inScope();
        if (found.isEmpty()) {
            LOGGER.quiet("No Gradle daemons are running.");
            return;
        }
        int stopped = 0;
        List<ManagedDaemon> remaining = new ArrayList<ManagedDaemon>();
        for (ManagedDaemon daemon : found) {
            if (daemon.stop()) {
                stopped++;
            } else {
                remaining.add(daemon);
            }
        }
        LOGGER.quiet(stopped == 1 ? "1 daemon stopped." : stopped + " daemons stopped.");
        for (ManagedDaemon daemon : remaining) {
            LOGGER.quiet("Could not stop the Gradle " + daemon.getGradleVersion() + " daemon with pid " + daemon.getPid() + ".");
        }
    }

    private void stopWhenIdle() {
        List<ManagedDaemon> found = inScope();
        if (found.isEmpty()) {
            LOGGER.quiet("No Gradle daemons are running.");
            return;
        }
        int asked = 0;
        for (ManagedDaemon daemon : found) {
            if (daemon.stopWhenIdle()) {
                asked++;
            }
        }
        LOGGER.quiet(asked == 1
            ? "1 daemon will stop once it finishes what it is doing."
            : asked + " daemons will stop once they finish what they are doing.");
    }

    private void cancel() {
        Long pid = parsePid();
        if (pid == null) {
            LOGGER.quiet("--cancel needs the process id of a daemon, as shown by --status.");
            return;
        }
        for (ManagedDaemon daemon : daemons.all()) {
            if (pid.equals(daemon.getPid())) {
                reportCancellation(daemon, daemon.cancelBuild());
                return;
            }
        }
        LOGGER.quiet("No Gradle daemon is running with pid " + pid + ".");
    }

    private void reportCancellation(ManagedDaemon daemon, ManagedDaemon.CancelOutcome outcome) {
        switch (outcome) {
            case REQUESTED:
                LOGGER.quiet("Asked the daemon with pid " + daemon.getPid() + " to cancel its build.");
                break;
            case NOTHING_RUNNING:
                LOGGER.quiet("The daemon with pid " + daemon.getPid() + " is not running a build.");
                break;
            case UNSUPPORTED:
                LOGGER.quiet("Gradle " + daemon.getGradleVersion() + " cannot cancel a build from outside the process that started it. "
                    + "Stop the daemon instead, or cancel from the client running the build.");
                break;
            case FAILED:
            default:
                LOGGER.quiet("Could not reach the daemon with pid " + daemon.getPid() + ".");
        }
    }

    private @Nullable Long parsePid() {
        try {
            return cancelPid == null ? null : Long.valueOf(cancelPid.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void start() {
        try {
            ManagedDaemon started = daemons.start(projectDir == null ? new File(".") : projectDir);
            LOGGER.quiet("Started a Gradle " + started.getGradleVersion() + " daemon with pid " + started.getPid() + ".");
        } catch (UnsupportedDaemonVersionException e) {
            LOGGER.quiet(e.getMessage());
        }
    }

    /**
     * The daemons this invocation acts on, newest version first so that the list reads the way a user
     * thinks about their versions.
     */
    private List<ManagedDaemon> inScope() {
        String currentVersion = GradleVersion.current().getVersion();
        List<ManagedDaemon> result = new ArrayList<ManagedDaemon>();
        for (ManagedDaemon daemon : daemons.all()) {
            if (allVersions || currentVersion.equals(daemon.getGradleVersion())) {
                result.add(daemon);
            }
        }
        Collections.sort(result, new Comparator<ManagedDaemon>() {
            @Override
            public int compare(ManagedDaemon left, ManagedDaemon right) {
                int byVersion = GradleVersion.version(right.getGradleVersion()).compareTo(GradleVersion.version(left.getGradleVersion()));
                if (byVersion != 0) {
                    return byVersion;
                }
                long leftPid = left.getPid() == null ? Long.MAX_VALUE : left.getPid();
                long rightPid = right.getPid() == null ? Long.MAX_VALUE : right.getPid();
                return Long.compare(leftPid, rightPid);
            }
        });
        return result;
    }
}
