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

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Stops daemon processes by PID, with safety checks defending against stale registry
 * entries and OS-recycled PIDs.
 *
 * <p>A naive PID-only stop would be unsafe: the daemon registry can hold stale entries
 * (a daemon crashed without removing itself), the OS recycles PIDs, and a recycled PID
 * may belong to an unrelated user process. Killing it would be incorrect.
 *
 * <p>Before {@code destroy(pid)}, this stopper requires both:
 *
 * <ol>
 *   <li><b>Port-bound check</b>: TCP-connect to the daemon's registered address with a
 *       short timeout. If the port is unbound ({@code ConnectException}), the daemon
 *       is dead and the registry entry is stale &mdash; do not kill.</li>
 *   <li><b>Process-info check</b>: read {@code ProcessHandle.info().arguments()} and
 *       require an exact match for {@code org.gradle.launcher.daemon.bootstrap.GradleDaemon}.
 *       This is the daemon JVM's main class (see {@code DefaultDaemonStarter.java:171}),
 *       stable since Gradle 5.0. Falls back to {@code commandLine()} when
 *       {@code arguments()} is unavailable on the platform. If both are empty, fails
 *       closed.</li>
 * </ol>
 *
 * <p>Why not the daemon wire protocol: a graceful stop over Gradle's socket protocol
 * requires pulling {@code daemon-messaging} and {@code client-services} (plus their
 * transitive messaging stack) into the consumer jar &mdash; substantial dependency
 * growth for a shaded distributable. The {@code ProcessHandle.destroy()} path sends
 * SIGTERM on POSIX (equivalent Windows terminate signal), which triggers the daemon's
 * JVM shutdown hook so the registry entry is cleaned up.
 *
 * <p>{@link ProcessHandle} is JDK 9+; the consumer jar bytecode-targets Java 8 so all
 * {@code ProcessHandle} interaction goes through reflection. On a Java 8 JVM, stopping
 * gracefully degrades to {@link StopResult#NOT_FOUND}. Tooling API consumers (IDEs)
 * ship on JDK 11+ in practice.
 */
final class DaemonStopper {

    /** Exact main-class identifier, stable since Gradle 5.0 (and earlier). */
    static final String DAEMON_MAIN_CLASS = "org.gradle.launcher.daemon.bootstrap.GradleDaemon";

    private static final int PORT_CHECK_TIMEOUT_MILLIS = 500;
    private static final long GRACEFUL_WAIT_MILLIS = 10_000L;

    private static final Method PROCESS_HANDLE_OF;
    private static final Method PROCESS_HANDLE_IS_ALIVE;
    private static final Method PROCESS_HANDLE_INFO;
    private static final Method PROCESS_HANDLE_DESTROY;
    private static final Method PROCESS_HANDLE_DESTROY_FORCIBLY;
    private static final Method PROCESS_HANDLE_ON_EXIT;
    private static final Method INFO_ARGUMENTS;
    private static final Method INFO_COMMAND_LINE;

    static {
        Method of = null, isAlive = null, info = null, destroy = null, destroyForcibly = null, onExit = null;
        Method args = null, cmdLine = null;
        try {
            Class<?> ph = Class.forName("java.lang.ProcessHandle");
            of = ph.getMethod("of", long.class);
            isAlive = ph.getMethod("isAlive");
            info = ph.getMethod("info");
            destroy = ph.getMethod("destroy");
            destroyForcibly = ph.getMethod("destroyForcibly");
            onExit = ph.getMethod("onExit");
            Class<?> infoIface = Class.forName("java.lang.ProcessHandle$Info");
            args = infoIface.getMethod("arguments");
            cmdLine = infoIface.getMethod("commandLine");
        } catch (ReflectiveOperationException ignored) {
            // Running on Java 8 — stopping is not supported.
        }
        PROCESS_HANDLE_OF = of;
        PROCESS_HANDLE_IS_ALIVE = isAlive;
        PROCESS_HANDLE_INFO = info;
        PROCESS_HANDLE_DESTROY = destroy;
        PROCESS_HANDLE_DESTROY_FORCIBLY = destroyForcibly;
        PROCESS_HANDLE_ON_EXIT = onExit;
        INFO_ARGUMENTS = args;
        INFO_COMMAND_LINE = cmdLine;
    }

    StopResult stop(DaemonInfoView daemon) {
        if (PROCESS_HANDLE_OF == null) {
            return StopResult.NOT_FOUND;
        }
        Long pidBoxed = daemon.context.pid;
        if (pidBoxed == null) {
            return StopResult.NOT_FOUND;
        }
        long pid = pidBoxed;

        Object processHandle = lookupAliveProcess(pid);
        if (processHandle == null) {
            return StopResult.NOT_FOUND;
        }

        // Safety check 1: is the daemon's port bound?
        if (!isPortBound(daemon.address.host.getHostAddress(), daemon.address.port)) {
            return StopResult.NOT_FOUND;
        }

        // Safety check 2: does the process look like a Gradle daemon?
        if (!looksLikeGradleDaemon(processHandle)) {
            return StopResult.NOT_FOUND;
        }

        return destroyAndWait(processHandle);
    }

    private static Object lookupAliveProcess(long pid) {
        try {
            Object optional = PROCESS_HANDLE_OF.invoke(null, pid);
            if (!(optional instanceof Optional) || !((Optional<?>) optional).isPresent()) {
                return null;
            }
            Object process = ((Optional<?>) optional).get();
            if (!(Boolean) PROCESS_HANDLE_IS_ALIVE.invoke(process)) {
                return null;
            }
            return process;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Opens a TCP connection to the daemon's registered host+port with a short
     * timeout. A successful connect (or one closed mid-handshake) means something
     * is listening; a {@code ConnectException} means the port is unbound and the
     * daemon is gone. Ambiguous failures (timeout, generic IOException) fail closed.
     */
    static boolean isPortBound(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PORT_CHECK_TIMEOUT_MILLIS);
            return true;
        } catch (ConnectException refused) {
            return false;
        } catch (IOException e) {
            // Timeout or other unknown error: do not kill.
            return false;
        }
    }

    /**
     * Checks {@code ProcessHandle.info().arguments()} for the daemon main class.
     * Falls back to {@code commandLine()} when arguments is unavailable. Returns
     * {@code false} (fail closed) when neither source can confirm.
     */
    private static boolean looksLikeGradleDaemon(Object processHandle) {
        try {
            Object info = PROCESS_HANDLE_INFO.invoke(processHandle);
            Object argsOptional = INFO_ARGUMENTS.invoke(info);
            if (argsOptional instanceof Optional && ((Optional<?>) argsOptional).isPresent()) {
                String[] args = (String[]) ((Optional<?>) argsOptional).get();
                for (String arg : args) {
                    if (DAEMON_MAIN_CLASS.equals(arg)) {
                        return true;
                    }
                }
                // arguments() succeeded but didn't contain the daemon main class —
                // definitively not a Gradle daemon.
                return false;
            }
            Object cmdLineOptional = INFO_COMMAND_LINE.invoke(info);
            if (cmdLineOptional instanceof Optional && ((Optional<?>) cmdLineOptional).isPresent()) {
                String cmdLine = (String) ((Optional<?>) cmdLineOptional).get();
                return cmdLine.contains(DAEMON_MAIN_CLASS);
            }
            // Neither arguments() nor commandLine() exposed (Windows-permission cases).
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static StopResult destroyAndWait(Object processHandle) {
        try {
            if (!(Boolean) PROCESS_HANDLE_DESTROY.invoke(processHandle)) {
                return StopResult.NOT_FOUND;
            }
            CompletableFuture<?> exit = (CompletableFuture<?>) PROCESS_HANDLE_ON_EXIT.invoke(processHandle);
            try {
                exit.get(GRACEFUL_WAIT_MILLIS, TimeUnit.MILLISECONDS);
                return StopResult.STOPPED;
            } catch (TimeoutException te) {
                PROCESS_HANDLE_DESTROY_FORCIBLY.invoke(processHandle);
                return StopResult.TIMED_OUT;
            }
        } catch (Exception e) {
            return StopResult.NOT_FOUND;
        }
    }
}
