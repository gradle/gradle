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

package org.gradle.launcher.daemon.client;

import com.google.common.base.Throwables;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.specs.ExplainingSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Pair;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.CountdownTimer;
import org.gradle.internal.time.Time;
import org.gradle.launcher.daemon.context.DaemonConnectDetails;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.protocol.Stop;
import org.gradle.launcher.daemon.protocol.StopWhenIdle;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <p>To stop a daemon:</p>
 *
 * <ul>
 * <li>The client creates a connection to daemon.</li>
 * <li>The client sends exactly one {@link org.gradle.launcher.daemon.protocol.Stop} message.</li>
 * <li>The daemon sends exactly one {@link org.gradle.launcher.daemon.protocol.Result} message. It may no longer send any messages.</li>
 * <li>The client sends a {@link org.gradle.launcher.daemon.protocol.Finished} message once it has received the {@link org.gradle.launcher.daemon.protocol.Result} message.
 *     It may no longer send any messages.</li>
 * <li>The client closes the connection.</li>
 * <li>The daemon closes the connection once it has received the {@link org.gradle.launcher.daemon.protocol.Finished} message.</li>
 * </ul>
 */
public class DaemonStopClient {
    private static final Logger LOGGER = Logging.getLogger(DaemonClient.class);
    private static final int STOP_TIMEOUT_SECONDS = 30;
    private final DaemonConnector connector;
    private final IdGenerator<UUID> idGenerator;
    private final StopDispatcher stopDispatcher;

    public DaemonStopClient(DaemonConnector connector, IdGenerator<UUID> idGenerator) {
        this.connector = connector;
        this.idGenerator = idGenerator;
        this.stopDispatcher = new StopDispatcher();
    }

    /**
     * Requests that the given daemons stop when idle. Does not block and returns before the daemons have all stopped.
     */
    public void gracefulStop(Collection<DaemonConnectDetails> daemons) {
        for (DaemonConnectDetails daemon : daemons) {
            DaemonClientConnection connection = connector.maybeConnect(daemon);
            if (connection == null) {
                continue;
            }
            try {
                LOGGER.debug("Requesting daemon {} stop when idle", daemon);
                stopDispatcher.dispatch(connection, new StopWhenIdle(idGenerator.generateId(), connection.getDaemon().getToken()));
                LOGGER.lifecycle("Gradle daemon stopped.");
            } finally {
                connection.stop();
            }
        }
    }

    /**
     * Stops all daemons, blocking until all have completed.
     */
    public void stop() {
        CountdownTimer timer = Time.startCountdownTimer(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        final Set<String> seen = new HashSet<String>();

        ExplainingSpec<DaemonContext> spec = new ExplainingSpec<DaemonContext>() {
            @Override
            public String whyUnsatisfied(DaemonContext element) {
                return "already seen";
            }

            @Override
            public boolean isSatisfiedBy(DaemonContext element) {
                return !seen.contains(element.getUid());
            }
        };

        DaemonClientConnection connection = connector.maybeConnect(spec);
        if (connection == null) {
            LOGGER.lifecycle(DaemonMessages.NO_DAEMONS_RUNNING);
            return;
        }

        LOGGER.lifecycle("Stopping Daemon(s)");

        //iterate and stop all daemons
        List<Long> stoppedPids = new ArrayList<>();
        while (connection != null && !timer.hasExpired()) {
            try {
                seen.add(connection.getDaemon().getUid());
                LOGGER.debug("Requesting daemon {} stop now", connection.getDaemon());
                boolean stopped = stopDispatcher.dispatch(connection, new Stop(idGenerator.generateId(), connection.getDaemon().getToken()));
                if (stopped) {
                    stoppedPids.add(connection.getDaemon().getPid());
                }
            } finally {
                connection.stop();
            }
            connection = connector.maybeConnect(spec);
        }

        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_1_9)) {
            waitForPidsToExit(stoppedPids, timer);
        } else {
            throw new IllegalStateException("Java 9 or later is required to stop daemons");
        }

        if (!stoppedPids.isEmpty()) {
            LOGGER.lifecycle(stoppedPids.size() + " Daemon" + ((stoppedPids.size() > 1) ? "s" : "") + " stopped");
        }

        if (connection != null) {
            throw new GradleException(String.format("Timeout waiting for all daemons to stop. Waited %s.", timer.getElapsed()));
        }
    }

    private static void waitForPidsToExit(List<Long> stoppedPids, CountdownTimer timer) {
        try {
            Class<?> processHandleClass = Class.forName("java.lang.ProcessHandle");
            MethodHandle ofMethod = MethodHandles.lookup().findStatic(processHandleClass, "of", MethodType.methodType(Optional.class, long.class));
            MethodHandle onExitMethod = MethodHandles.lookup().findVirtual(processHandleClass, "onExit", MethodType.methodType(CompletableFuture.class));

            List<Pair<?, CompletableFuture<?>>> stoppedProcesses = new ArrayList<>();
            for (Long pid : stoppedPids) {
                Optional<?> processHandle = (Optional<?>) ofMethod.invoke(pid);
                if (processHandle.isPresent()) {
                    stoppedProcesses.add(Pair.of(processHandle.get(), (CompletableFuture<?>) onExitMethod.invoke(processHandle.get())));
                }
            }
            CompletableFuture<Void> waitForPids = CompletableFuture.allOf(
                stoppedProcesses.stream().map(Pair::right).toArray(CompletableFuture[]::new)
            );
            try {
                waitForPids.get(timer.getRemainingMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            } catch (TimeoutException e) {
                LOGGER.lifecycle("Timeout waiting for daemon processes to exit");
            }
        } catch (Throwable e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }
}
