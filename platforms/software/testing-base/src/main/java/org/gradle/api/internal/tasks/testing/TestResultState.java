/*
 * Copyright 2024 the original author or authors.
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

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks the number of tests and successes/failures and the hierarchy of test results.
 *
 * Given a hierarchy
 * Root > Group > Test 1...5
 *
 * Test 1...5 will have a parent of Group, and Group will have a parent of Root.
 * Composite test results are calculated by summing the results of all children. This is done by incrementing the parent's count when a child's count is incremented.
 */
@NullMarked
class TestResultState {
    private final @Nullable TestResultState parent;
    private final AtomicLong totalCount = new AtomicLong(0);
    private final AtomicLong successfulCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);

    TestResultState(@Nullable TestResultState parent) {
        this.parent = parent;
    }

    long getTotalCount() {
        return totalCount.get();
    }

    void incrementTotalCount() {
        totalCount.incrementAndGet();
        if (parent!=null) {
            parent.incrementTotalCount();
        }
    }

    long getSuccessfulCount() {
        return successfulCount.get();
    }
    void incrementSuccessfulCount() {
        successfulCount.incrementAndGet();
        if (parent!=null) {
            parent.incrementSuccessfulCount();
        }
    }

    long getFailureCount() {
        return failureCount.get();
    }
    void incrementFailureCount() {
        failureCount.incrementAndGet();
        if (parent!=null) {
            parent.incrementFailureCount();
        }
    }
}
