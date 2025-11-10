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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.testing.TestFilter;
import org.gradle.api.tasks.testing.TestFrameworkOptions;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.internal.time.Clock;
import org.gradle.process.internal.worker.WorkerProcessBuilder;

import java.io.Closeable;

@UsedByScanPlugin("test-retry")
public interface TestFramework extends Closeable, Describable {

    /**
     * Returns a copy of the test framework but with the specified test filters.
     *
     * @param newTestFilters new test filters
     * @return test framework with new test filters
     */
    @UsedByScanPlugin("test-retry")
    TestFramework copyWithFilters(TestFilter newTestFilters);

    /**
     * Returns a detector which is used to determine which of the candidate class files correspond to test classes to be
     * executed.
     */
    @Internal
    TestFrameworkDetector getDetector();

    @Nested
    TestFrameworkOptions getOptions();

    /**
     * Returns a factory which is used to create a {@link TestDefinitionProcessor} in
     * each worker process. This factory is serialized across to the worker process, and then its {@link
     * WorkerTestDefinitionProcessorFactory#create(IdGenerator, ActorFactory, Clock)}
     * method is called to create the test processor.
     */
    @Internal
    WorkerTestDefinitionProcessorFactory<?> getProcessorFactory();

    /**
     * Returns an action which is used to perform some framework specific worker process configuration. This action is
     * executed before starting each worker process.
     */
    @Internal
    Action<WorkerProcessBuilder> getWorkerConfigurationAction();

    /**
     * Returns additional levels to skip for {@code AbstractTestTask.getReportEntrySkipLevels()}.
     * Default implementation returns 0.
     */
    @Internal
    default int getAdditionalReportEntrySkipLevels() {
        return 0;
    }

    /**
     * Whether or not this test framework supports resource-based (non-class-based) test definitions.
     *
     * @return {@code true} if resource-based test definitions are supported; otherwise {@code false}
     */
    default boolean supportsNonClassBasedTesting() {
        return false;
    }

    @Override
    @Internal
    String getDisplayName();
}
