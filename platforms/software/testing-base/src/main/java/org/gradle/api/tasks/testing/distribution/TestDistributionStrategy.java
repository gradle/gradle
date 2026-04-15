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

package org.gradle.api.tasks.testing.distribution;

import org.gradle.api.Incubating;
import org.jspecify.annotations.NullMarked;

/**
 * Controls how daemon-side discovered tests are grouped before being sent to worker processes.
 *
 * @since 9.6.0
 */
@Incubating
@NullMarked
public enum TestDistributionStrategy {
    /**
     * Distributes tests at top-level container granularity.
     * <p>
     * All tests from the same top-level container (test class, resource file, or directory)
     * are guaranteed to be sent to the same worker. This preserves correctness for
     * class-level lifecycle methods ({@code @BeforeAll}, {@code @AfterAll}), nested class
     * hierarchies, ordered execution ({@code @Stepwise}), shared mutable static state,
     * and resource-based test files discovered by custom test engines.
     *
     * @since 9.6.0
     */
    BY_TOP_TEST_CONTAINER,

    /**
     * Distributes each discovered test individually with no grouping.
     * <p>
     * Each leaf test identifier is sent to the processor as a separate unit.
     * This does <em>not</em> preserve class cohesion — methods from the same class may
     * be distributed to different workers. Use only when tests are fully independent
     * and do not rely on class-level lifecycle or shared state.
     *
     * @since 9.6.0
     */
    BY_INDIVIDUAL_TEST
}
