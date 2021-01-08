/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.launcher.daemon.server.exec;

import org.gradle.initialization.BuildEventConsumer;
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * An event consumer that asynchronously dispatches events to the client.
 */
class DaemonConnectionBackedEventConsumer implements BuildEventConsumer {
    private final DaemonCommandExecution execution;
    private final BlockingQueue<Object> queue = new LinkedBlockingQueue<Object>();
    private final ForwardEvents forwarder = new ForwardEvents();

    public DaemonConnectionBackedEventConsumer(DaemonCommandExecution execution) {
        this.execution = execution;
        forwarder.start();
    }

    @Override
    public void dispatch(Object event) {
        queue.offer(event);
    }

    public void waitForFinish() {
        forwarder.waitForFinish();
    }

    private class ForwardEvents extends Thread {
        private volatile boolean stopped;
        private boolean ableToSend = true;

        public ForwardEvents() {
            super("Daemon client event forwarder");
        }

        @Override
        public void run() {
            while (moreMessagesToSend()) {
                Object event = getNextEvent();
                if (event != null) {
                    dispatchEvent(event);
                }
            }
        }

        private boolean moreMessagesToSend() {
            return ableToSend && !(stopped && queue.isEmpty());
        }

        private Object getNextEvent() {
            try {
                return queue.poll(10, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                stopped = true;
                return null;
            }
        }

        private void dispatchEvent(Object event) {
            try {
                execution.getConnection().event(event);
            } catch (RuntimeException e) {
                ableToSend = false;
            }
        }

        public void waitForFinish() {
            stopped = true;
            try {
                join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
