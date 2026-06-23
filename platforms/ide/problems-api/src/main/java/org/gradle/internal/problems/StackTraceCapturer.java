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

package org.gradle.internal.problems;

import com.google.common.base.Supplier;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Captures the stack trace that locates a problem, within per-stream budgets.
 */
final class StackTraceCapturer {

    private final AtomicInteger remainingFull;
    private final AtomicInteger remainingBounded;
    private final BoundedCallerStackCapturer boundedCallerStackCapturer;

    StackTraceCapturer(int fullBudget, int boundedBudget, BoundedCallerStackCapturer boundedCallerStackCapturer) {
        this.remainingFull = new AtomicInteger(fullBudget);
        this.remainingBounded = new AtomicInteger(boundedBudget);
        this.boundedCallerStackCapturer = boundedCallerStackCapturer;
    }

    @Nullable
    Throwable captureCaller() {
        if (remainingFull.getAndDecrement() > 0) {
            return new Exception();
        }
        if (remainingBounded.getAndDecrement() > 0) {
            return boundedCallerStackCapturer.captureCallerStack();
        }
        return null;
    }

    @Nullable
    Throwable captureSupplied(Supplier<? extends Throwable> factory) {
        if (remainingFull.getAndDecrement() > 0) {
            return factory.get();
        }
        return null;
    }
}
