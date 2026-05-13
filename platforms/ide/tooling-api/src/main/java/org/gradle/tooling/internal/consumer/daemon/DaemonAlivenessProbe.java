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
package org.gradle.tooling.internal.consumer.daemon;

import org.jspecify.annotations.Nullable;

/**
 * Checks whether a daemon entry from the registry corresponds to a live Gradle
 * daemon process. Uses {@link ProcessHandles} only — no network I/O — so the check
 * is silent (does not perturb a running daemon with a spurious TCP connect) and
 * fast (kernel-local syscalls, typically microseconds per check).
 *
 * <p>Two operating modes:
 * <ul>
 *   <li>{@code LENIENT} (listing filter): when the OS does not expose the
 *       process's arguments/command line, trust the registry. Avoids hiding live
 *       daemons on Windows where {@code ProcessHandle.Info.arguments()} can return
 *       empty due to permission restrictions.</li>
 *   <li>{@code STRICT} (stop safety): the same scenarios fail closed — better to
 *       leave a daemon running than to signal an unverified process.</li>
 * </ul>
 *
 * <p>This check is strictly stronger than checking only that the daemon's TCP port
 * is bound: a recycled PID that happens to bind the same port (rare, but possible)
 * would be accepted by a port-only check but rejected here.
 */
final class DaemonAlivenessProbe {

    enum Mode { LENIENT, STRICT }

    private DaemonAlivenessProbe() {
    }

    /**
     * Returns the live {@code ProcessHandle} for the given daemon, or {@code null}
     * if the daemon is gone (registry entry is stale), the PID has been recycled
     * to a non-daemon process, or — in {@link Mode#STRICT} — the daemon identity
     * cannot be verified on this platform.
     */
    @Nullable
    static Object verifiedHandle(DaemonInfoView daemon, Mode mode) {
        Long pidBoxed = daemon.context.pid;
        if (pidBoxed == null) {
            return null;
        }
        Object process = ProcessHandles.liveProcessHandle(pidBoxed);
        if (process == null) {
            return null;
        }
        Boolean isDaemon = ProcessHandles.isGradleDaemon(process);
        if (Boolean.FALSE.equals(isDaemon)) {
            return null;
        }
        if (isDaemon == null && mode == Mode.STRICT) {
            return null;
        }
        return process;
    }

    /**
     * Lightweight wrapper around {@link #verifiedHandle(DaemonInfoView, Mode)} for
     * callers that only need the boolean answer.
     */
    static boolean isAlive(DaemonInfoView daemon, Mode mode) {
        return verifiedHandle(daemon, mode) != null;
    }
}
