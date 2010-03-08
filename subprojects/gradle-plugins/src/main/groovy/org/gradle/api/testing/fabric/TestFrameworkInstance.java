/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.testing.fabric;

import org.gradle.api.Action;
import org.gradle.api.tasks.testing.TestFrameworkOptions;
import org.gradle.api.tasks.util.JavaForkOptions;
import org.gradle.api.testing.execution.fork.WorkerTestClassProcessorFactory;
import org.gradle.process.WorkerProcessBuilder;

/**
 * @author Tom Eyckmans
 */
public interface TestFrameworkInstance {
    TestFramework getTestFramework();

    TestFrameworkDetector getDetector();

    void initialize();

    void report();

    TestFrameworkOptions getOptions();

    void applyForkArguments(JavaForkOptions forkOptions);

    WorkerTestClassProcessorFactory getProcessorFactory();

    Action<WorkerProcessBuilder> getWorkerConfigurationAction();
}
