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
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.testing.TestFilter;
import org.gradle.api.tasks.testing.TestFrameworkOptions;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.process.internal.worker.WorkerProcessBuilder;

import java.io.Closeable;
import java.util.Collections;
import java.util.List;

@UsedByScanPlugin("test-retry")
public interface TestFramework extends Closeable {

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
     * Returns a factory which is used to create a {@link org.gradle.api.internal.tasks.testing.TestClassProcessor} in
     * each worker process. This factory is serialized across to the worker process, and then its {@link
     * org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory#create(org.gradle.internal.service.ServiceRegistry)}
     * method is called to create the test processor.
     */
    @Internal
    WorkerTestClassProcessorFactory getProcessorFactory();

    /**
     * Returns an action which is used to perform some framework specific worker process configuration. This action is
     * executed before starting each worker process.
     */
    @Internal
    Action<WorkerProcessBuilder> getWorkerConfigurationAction();

    /**
     * Returns a list of distribution modules that the test worker requires on the application classpath.
     * These dependencies are loaded from the Gradle distribution.
     *
     * Application classes specified by {@link WorkerProcessBuilder#sharedPackages} are
     * also included in the implementation classpath.
     *
     * @see #getUseDistributionDependencies()
     */
    @Internal
    default List<TestFrameworkDistributionModule> getWorkerApplicationClasspathModules() {
        return Collections.emptyList();
    }

    /**
     * Returns a list of distribution modules that the test worker requires on the application modulepath if it runs as a module.
     * These dependencies are loaded from the Gradle distribution.
     *
     * Application classes specified by {@link WorkerProcessBuilder#sharedPackages} are
     * also included in the implementation classpath.
     *
     * @see #getUseDistributionDependencies()
     */
    @Internal
    default List<TestFrameworkDistributionModule> getWorkerApplicationModulepathModules() {
        return Collections.emptyList();
    }

    /**
     * Returns a list of distribution modules that the test worker requires on implementation the classpath.
     * These dependencies are loaded from the Gradle distribution.
     *
     * @see #getUseDistributionDependencies()
     */
    @Internal
    default List<TestFrameworkDistributionModule> getWorkerImplementationClasspathModules() {
        return Collections.emptyList();
    }

    /**
     * Returns a list of distribution modules that the test worker requires on the implementation modulepath if it runs as a module.
     * These dependencies are loaded from the Gradle distribution.
     *
     * @see #getUseDistributionDependencies()
     */
    @Internal
    default List<TestFrameworkDistributionModule> getWorkerImplementationModulepathModules() {
        return Collections.emptyList();
    }

    /**
     * Whether the legacy behavior of loading test framework dependencies from the Gradle distribution
     * is enabled. If true, jars specified by this framework are loaded from the Gradle distribution
     * and placed on the test worker implementation/application classpath/modulepath.
     * <p>
     * This functionality is legacy and will eventually be deprecated and removed. Test framework dependencies
     * should be managed externally from the Gradle distribution, as is done by test suites.
     *
     * @return Whether test framework dependencies should be loaded from the Gradle distribution.
     */
    @Internal
    boolean getUseDistributionDependencies();

}
