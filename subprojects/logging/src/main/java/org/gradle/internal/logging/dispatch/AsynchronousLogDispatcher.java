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

package org.gradle.internal.logging.dispatch;

import org.gradle.internal.logging.events.OutputEvent;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

abstract public class AsynchronousLogDispatcher extends Thread {
    private final CountDownLatch completionLock = new CountDownLatch(1);
    private final Queue<OutputEvent> eventQueue = new ConcurrentLinkedQueue<OutputEvent>();
    private volatile boolean shouldStop;
    private boolean unableToSend;

    public AsynchronousLogDispatcher(String name) {
        super(name);
    }

    public void submit(OutputEvent event) {
        eventQueue.add(event);
    }

    @Override
    public void run() {
        try {
            while (!shouldStop) {
                OutputEvent event = eventQueue.poll();
                if (event == null) {
                    Thread.sleep(10);
                } else {
                    dispatchAsync(event);
                }
            }
        } catch (InterruptedException ex) {
            // we must not use interrupt() because it would automatically
            // close the connection (sending data from an interrupted thread
            // automatically closes the connection)
            shouldStop = true;
        }
        flush();
        completionLock.countDown();
    }

    public void flush() {
        OutputEvent event;
        while ((event = eventQueue.poll()) != null) {
            dispatchAsync(event);
        }
    }

    private void dispatchAsync(OutputEvent event) {
        if (unableToSend) {
            return;
        }
        try {
            processEvent(event);
        } catch (Exception ex) {
            shouldStop = true;
            unableToSend = true;
            //Ignore. It means the client has disconnected so no point sending him any log output.
            //we should be checking if client still listens elsewhere anyway.
        }
    }

    abstract public void processEvent(OutputEvent event);

    public void waitForCompletion() {
        cleanup();
        shouldStop = true;
        try {
            completionLock.await();
        } catch (InterruptedException e) {
            // the caller has been interrupted
        }
    }

    public void cleanup() {
        // override if necessary
    }
}
