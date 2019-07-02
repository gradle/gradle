/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * Represents the configuration of a worker.  Used when submitting an item of work
 * to the {@link WorkerExecutor}.
 *
 * <pre>
 *      workerExecutor.submit(MyWorkerExecution.class) { WorkerSpec spec -&gt;
 *          spec.isolationMode = IsolationMode.PROCESS
 *
 *          forkOptions { JavaForkOptions options -&gt;
 *              options.maxHeapSize = "512m"
 *              options.systemProperty 'some.prop', 'value'
 *              options.jvmArgs "-server"
 *          }
 *
 *          classpath configurations.fooLibrary
 *
 *          // parameters are specific to the WorkerExecution class
 *          spec.parameters {
 *              foo = "bar"
 *              files.from("some/file")
 *          }
 *      }
 * </pre>
 *
 * @param <T> Parameter type for the worker execution. Should be {@link WorkerParameters.None} if the execution does not have parameters.
 * @since 5.6
 **/
@Incubating
public interface WorkerSpec<T extends WorkerParameters> extends BaseWorkerSpec {
    /**
     * Returns the parameters object to be used with this item of work.
     */
    T getParameters();

    /**
     * Configures the parameters object for this item of work.
     */
    void parameters(Action<T> action);
}
