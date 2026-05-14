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

package org.gradle.internal.work;

import java.util.function.BooleanSupplier;

public interface SubmissionQueue {
    void add(Runnable task);

    /**
     * Process work from this queue on the current thread until the queue is empty or
     * the given stopping condition is met.
     *
     * <p>
     * Only processes work if needed. Some implementations may choose to not process work
     * if there is already sufficient concurrency.
     *
     * @param stoppingCondition a condition that indicates when to stop processing work
     */
    void processWorkUsingCurrentThreadUntilEmptyOr(BooleanSupplier stoppingCondition);
}
