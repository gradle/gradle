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

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Reflective access to {@link java.lang.ProcessHandle} (JDK 9+).
 *
 * <p>The Tooling API consumer jar bytecode-targets Java 8, so all interaction with
 * {@code ProcessHandle} has to go through reflection. On a Java 8 runtime the
 * static initializer leaves every method handle {@code null} and {@link
 * #isAvailable()} returns false; callers then degrade gracefully.
 */
final class ProcessHandles {

    /** The daemon JVM's main-class argument; stable from Gradle 5.0+ (see {@code DefaultDaemonStarter.java:171}). */
    static final String DAEMON_MAIN_CLASS = "org.gradle.launcher.daemon.bootstrap.GradleDaemon";

    private static final Method PH_OF;
    private static final Method PH_IS_ALIVE;
    private static final Method PH_INFO;
    private static final Method PH_DESTROY;
    private static final Method PH_DESTROY_FORCIBLY;
    private static final Method PH_ON_EXIT;
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
            // Running on Java 8.
        }
        PH_OF = of;
        PH_IS_ALIVE = isAlive;
        PH_INFO = info;
        PH_DESTROY = destroy;
        PH_DESTROY_FORCIBLY = destroyForcibly;
        PH_ON_EXIT = onExit;
        INFO_ARGUMENTS = args;
        INFO_COMMAND_LINE = cmdLine;
    }

    private ProcessHandles() {
    }

    static boolean isAvailable() {
        return PH_OF != null;
    }

    /**
     * Returns the {@code ProcessHandle} for {@code pid} if the process exists and is
     * alive, or {@code null} otherwise. Never throws.
     */
    @Nullable
    static Object liveProcessHandle(long pid) {
        if (PH_OF == null) {
            return null;
        }
        try {
            Object opt = PH_OF.invoke(null, pid);
            if (!(opt instanceof Optional) || !((Optional<?>) opt).isPresent()) {
                return null;
            }
            Object process = ((Optional<?>) opt).get();
            return (Boolean) PH_IS_ALIVE.invoke(process) ? process : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Tri-state daemon identity check.
     * <ul>
     *   <li>{@code TRUE}: the process's {@code info().arguments()} or {@code commandLine()}
     *       explicitly contains the Gradle daemon main class.</li>
     *   <li>{@code FALSE}: the info is exposed but does NOT contain the main class —
     *       definitely not a Gradle daemon (e.g. PID has been recycled).</li>
     *   <li>{@code null}: the OS or permission model does not expose the info
     *       (typical on Windows for some processes). Caller must decide whether to
     *       trust the registry or fail closed.</li>
     * </ul>
     */
    @Nullable
    static Boolean isGradleDaemon(Object processHandle) {
        if (processHandle == null || PH_INFO == null) {
            return null;
        }
        try {
            Object info = PH_INFO.invoke(processHandle);
            Object argsOptional = INFO_ARGUMENTS.invoke(info);
            if (argsOptional instanceof Optional && ((Optional<?>) argsOptional).isPresent()) {
                String[] args = (String[]) ((Optional<?>) argsOptional).get();
                for (String arg : args) {
                    if (DAEMON_MAIN_CLASS.equals(arg)) {
                        return Boolean.TRUE;
                    }
                }
                return Boolean.FALSE;
            }
            Object cmdLineOptional = INFO_COMMAND_LINE.invoke(info);
            if (cmdLineOptional instanceof Optional && ((Optional<?>) cmdLineOptional).isPresent()) {
                String cmdLine = (String) ((Optional<?>) cmdLineOptional).get();
                return cmdLine.contains(DAEMON_MAIN_CLASS) ? Boolean.TRUE : Boolean.FALSE;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    static boolean destroy(Object processHandle) {
        try {
            return (Boolean) PH_DESTROY.invoke(processHandle);
        } catch (Exception e) {
            return false;
        }
    }

    static void destroyForcibly(Object processHandle) {
        try {
            PH_DESTROY_FORCIBLY.invoke(processHandle);
        } catch (Exception ignored) {
            // best effort
        }
    }

    @Nullable
    static CompletableFuture<?> onExit(Object processHandle) {
        try {
            return (CompletableFuture<?>) PH_ON_EXIT.invoke(processHandle);
        } catch (Exception e) {
            return null;
        }
    }
}
