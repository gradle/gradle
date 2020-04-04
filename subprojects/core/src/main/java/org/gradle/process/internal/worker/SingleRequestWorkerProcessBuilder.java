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

/**
 * Configures and builds single request workers. A single request worker runs each request in a separate forked worker process.
 *
 * <p>This builder produces instances of type {@link RequestHandler}. Each call to {@link RequestHandler#run(Object)} on the returned object will start a worker process,
 * run the method in the worker and will block until the result is received by the caller and the worker process has stopped.
 * Any exception thrown by the worker method is rethrown to the caller.
 *
 * <p>The worker process executes the request using an instance of the implementation type specified as a parameter to {@link WorkerProcessFactory#singleRequestWorker(Class)}.</p>
 */
public interface SingleRequestWorkerProcessBuilder<IN, OUT> extends WorkerProcessSettings {
    /**
     * Creates the worker. The returned value can be used to run multiple requests, each will run in a separate worker process.
     */
    RequestHandler<IN, OUT> build();
}
