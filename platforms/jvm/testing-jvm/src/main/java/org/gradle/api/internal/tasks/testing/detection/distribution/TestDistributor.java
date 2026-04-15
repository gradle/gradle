/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.detection.distribution;

import org.gradle.api.internal.tasks.testing.TestDefinition;
import org.gradle.api.internal.tasks.testing.TestDefinitionProcessor;
import org.jspecify.annotations.NullMarked;
import org.junit.platform.launcher.TestPlan;

import java.util.List;

/**
 * Distributes discovered tests from a {@link TestPlan} to a {@link TestDefinitionProcessor}.
 * <p>
 * Implementations control how tests are grouped before being sent to worker processes.
 * The grouping strategy determines which tests execute together in the same JVM, affecting
 * correctness of lifecycle methods, shared state, and execution ordering.
 */
@NullMarked
public interface TestDistributor {
    /**
     * Distributes tests from the given plan to the processor.
     * <p>
     * The distributor may send a subset of identifiers from the plan (for example, only
     * class-level containers rather than individual method identifiers). The worker is
     * responsible for re-discovering the full set of tests within each distributed identifier.
     *
     * @param testPlan the discovered test plan to distribute
     * @param processor the processor to send test definitions to
     * @return the groups of test definitions that were distributed, where each inner list
     *         represents the definitions sent together as a single unit of work
     */
    List<List<TestDefinition>> distribute(TestPlan testPlan, TestDefinitionProcessor<TestDefinition> processor);
}
