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

import org.gradle.tooling.daemon.StopResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Stops daemon processes by PID, with safety checks defending against stale registry
 * entries and OS-recycled PIDs.
 *
 * <p>Before signaling a process, {@link DaemonAlivenessProbe} in {@link
 * DaemonAlivenessProbe.Mode#STRICT STRICT} mode verifies both that (a) the PID is
 * alive and (b) the process is identifiably a Gradle daemon (its launch arguments
 * contain {@code org.gradle.launcher.daemon.bootstrap.GradleDaemon}). Failures in
 * either check, or platforms that hide the launch arguments (Windows-permission
 * edge cases), return {@link StopResult#NOT_FOUND} — we never send a signal to a
 * process we cannot positively identify.
 *
 * <p>Why not the daemon wire protocol: a graceful stop over Gradle's socket protocol
 * requires pulling {@code daemon-messaging} and {@code client-services} (plus their
 * transitive messaging stack) into the consumer jar — substantial dependency growth
 * for a shaded distributable. {@code ProcessHandle.destroy()} sends SIGTERM on POSIX
 * (equivalent Windows terminate signal), which triggers the daemon's JVM shutdown
 * hook so the registry entry is cleaned up.
 *
 * <p>On a Java 8 runtime, {@link ProcessHandles#isAvailable()} returns false and
 * every stop returns {@link StopResult#NOT_FOUND}. Tooling API consumers (IDEs)
 * ship on JDK 11+ in practice.
 */
final class DaemonStopper {

    private static final long GRACEFUL_WAIT_MILLIS = 10_000L;

    StopResult stop(DaemonInfoView daemon) {
        Object processHandle = DaemonAlivenessProbe.verifiedHandle(daemon, DaemonAlivenessProbe.Mode.STRICT);
        if (processHandle == null) {
            return StopResult.NOT_FOUND;
        }
        return destroyAndWait(processHandle);
    }

    private static StopResult destroyAndWait(Object processHandle) {
        if (!ProcessHandles.destroy(processHandle)) {
            return StopResult.NOT_FOUND;
        }
        CompletableFuture<?> exit = ProcessHandles.onExit(processHandle);
        if (exit == null) {
            return StopResult.STOPPED;
        }
        try {
            exit.get(GRACEFUL_WAIT_MILLIS, TimeUnit.MILLISECONDS);
            return StopResult.STOPPED;
        } catch (TimeoutException te) {
            ProcessHandles.destroyForcibly(processHandle);
            return StopResult.TIMED_OUT;
        } catch (Exception e) {
            return StopResult.NOT_FOUND;
        }
    }
}
