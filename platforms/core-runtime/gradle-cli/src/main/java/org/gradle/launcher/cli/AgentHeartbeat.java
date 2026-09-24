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

import com.google.common.annotations.VisibleForTesting;
import org.jspecify.annotations.NullMarked;

import java.io.Closeable;
import java.io.PrintStream;

/**
 * Periodically writes a newline, so that whatever launched Gradle in agent mode can tell the otherwise silent process is still alive.
 */
@NullMarked
class AgentHeartbeat implements Closeable {
    static final String INTERVAL_MILLIS_SYSTEM_PROPERTY = "org.gradle.internal.agent.heartbeat.millis";
    private static final long DEFAULT_INTERVAL_MILLIS = 10_000;

    private final Thread thread;
    private volatile boolean closed;

    private AgentHeartbeat(PrintStream output, long intervalMillis) {
        thread = new Thread(() -> run(output, intervalMillis), "Agent mode heartbeat");
        thread.setDaemon(true);
    }

    /**
     * Must be called before the logging manager replaces {@code System.err}.
     */
    static AgentHeartbeat start() {
        return start(System.err, Long.getLong(INTERVAL_MILLIS_SYSTEM_PROPERTY, DEFAULT_INTERVAL_MILLIS));
    }

    @VisibleForTesting
    static AgentHeartbeat start(PrintStream output, long intervalMillis) {
        AgentHeartbeat heartbeat = new AgentHeartbeat(output, intervalMillis);
        heartbeat.thread.start();
        return heartbeat;
    }

    private void run(PrintStream output, long intervalMillis) {
        while (!closed) {
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                // Only close() is expected to interrupt, but an unexpected interrupt must not silence the heartbeat
                continue;
            }
            // Not println(), which would write the platform line separator
            output.write('\n');
            output.flush();
        }
    }

    @Override
    public void close() {
        closed = true;
        thread.interrupt();
    }
}
