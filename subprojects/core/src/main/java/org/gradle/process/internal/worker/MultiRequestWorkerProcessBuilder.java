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
import org.gradle.internal.serialize.Serializer;

/**
 * Configures and builds multi-request workers. A multi-request worker runs zero or more requests in a forked worker process.
 *
 * <p>This builder produces instances of type {@link MultiRequestClient}. Each call to {@link MultiRequestClient#run(Object)} on the returned object will run the method in the worker and block until the result is received.
 * Any exception thrown by the worker method is rethrown to the caller.
 *
 * <p>The worker process executes the request using an instance of the implementation type specified as a parameter to {@link WorkerProcessFactory#multiRequestWorker(Class)}.</p>
 *
 * <p>The worker process must be explicitly started and stopped using the methods on {@link WorkerControl}.</p>
 */
public interface MultiRequestWorkerProcessBuilder<IN, OUT> extends WorkerProcessSettings {
    /**
     * Creates a worker.
     *
     * <p>The worker process is not started until {@link WorkerControl#start()} is called on the returned object.</p>
     */
    MultiRequestClient<IN, OUT> build();

    /**
     * Registers a callback to invoke if a failure in an underlying process is detected.
     */
    void onProcessFailure(Action<WorkerProcess> action);

    /**
     * Registers a serializer to use when handling arguments to methods of {@link T}.
     */
    <T> void registerArgumentSerializer(Class<T> type, Serializer<T> serializer);

    /**
     * Use a simpler classloader structure where everything is in the application classloader.
     */
    void useApplicationClassloaderOnly();
}
