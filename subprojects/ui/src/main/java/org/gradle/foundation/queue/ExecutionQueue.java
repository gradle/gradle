/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.foundation.queue;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class abstracts running multiple tasks consecutively. This exists because I'm not certain that Gradle is thread-safe and on Windows, running tasks that require lots of disk I/O get
 * considerably slower when run concurrently. This will allow requests to be made and they will run as soon as any previous requests have finished.
 */
public class ExecutionQueue<R extends ExecutionQueue.Request> {
    private final Logger logger = Logging.getLogger(ExecutionQueue.class);
    private volatile LinkedBlockingQueue<R> requests = new LinkedBlockingQueue<R>();

    private Thread executionThread;

    /**
     * This removes the complexities of managing queued up requests across threads. Implement this to define what to do when a request is made.
     */
    public interface ExecutionInteraction<R> {
        /**
         * When this is called, execute the given request.
         *
         * @param request the request to execute.
         */
        void execute(R request);
    }

    /**
     * Marker interface for a request. It contains the command line to execute and some other information.
     */
    public interface Request {

        /**
         * Marker interface for types. This defines a high-level category of this request (Refresh and Execution are the only types at the moment).
         */
        public interface Type {
        }

        /**
         * @return the type of request.
         */
        public Type getType();
    }

    public interface RequestCancellation {
        void onCancel(Request request);
    }

    public ExecutionQueue(ExecutionInteraction<R> executeInteraction) {
        executionThread = new Thread(new ExecutionThread(executeInteraction));

        //This seems to solve some classloader issues. Actually, I did this in the SwingWorker thread when refreshing the tasks
        //and it cleared up SOME problems, so I'm doing it here as well.
        executionThread.setContextClassLoader(getClass().getClassLoader());
        executionThread.start();
    }

    /**
     * Call this to add a task to the execution queue. It will be executed as soon as the current task has completed.
     *
     * @param request the requested task
     */
    public void addRequestToQueue(R request) {
        requests.offer(request);
    }

    public boolean removeRequestFromQueue(Request request) {
        return requests.remove(request);
    }

    /**
     * This waits until the next request is available.
     *
     * @return the next request.
     */
    private R getNextAvailableRequest() {
        try {
            return requests.take();
        } catch (InterruptedException e) {
            logger.error("Getting next available request", e);
            return null;
        }
    }

    /**
     * This thread actually launches the gradle commands. It waits until a new request is added, then it executes it.
     */
    private class ExecutionThread implements Runnable {
        private ExecutionInteraction<R> executeInteraction;

        private ExecutionThread(ExecutionInteraction<R> executeInteraction) {
            this.executeInteraction = executeInteraction;
        }

        public void run() {
            while (true) {
                R request = getNextAvailableRequest();
                if (request == null) {
                    return;
                }
                executeInteraction.execute(request);
            }
        }
    }
}
