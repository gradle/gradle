/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal.worker;

import org.gradle.api.Action;

public interface WorkerProcessFactory {
    /**
     * Creates a builder for workers that will run the given action. The worker action is serialized to the worker process and executed.
     *
     * <p>The worker process is not started until {@link WorkerProcess#start()} is called.</p>
     *
     * @param workerAction The action to serialize and run in the worker process.
     */
    WorkerProcessBuilder create(Action<? super WorkerProcessContext> workerAction);

    /**
     * Creates a builder for workers that will handle requests using the given worker implementation, with each request executed in a separate worker process.
     *
     * <p>The worker process is not started until a method on the return value of {@link SingleRequestWorkerProcessBuilder#build()} is called.</p>
     *
     * @param protocolType An interface that represents the requests that can be handled by the worker.
     * @param workerImplementation The implementation class to run in the worker process.
     */
    <P> SingleRequestWorkerProcessBuilder<P> singleRequestWorker(Class<P> protocolType, Class<? extends P> workerImplementation);

    /**
     * Creates a builder for workers that will handle requests using the given worker implementation, with a worker process handling zero or more requests.
     * A worker process handles a single request at a time.
     *
     * <p>The worker process is not started until {@link WorkerControl#start()} is called.</p>
     *
     * @param workerType An interface that clients use to dispatch requests to the worker. Should also extend {@link WorkerControl}, to allow the worker to be started and stopped.
     * @param protocolType An interface that represents the requests that can be handled by the worker.
     * @param workerImplementation The implementation class to run in the worker process.
     */
    <P, W extends P> MultiRequestWorkerProcessBuilder<W> multiRequestWorker(Class<W> workerType,
                                                                            Class<P> protocolType,
                                                                            Class<? extends P> workerImplementation);
}
