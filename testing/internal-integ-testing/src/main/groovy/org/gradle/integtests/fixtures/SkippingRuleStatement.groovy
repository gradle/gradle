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

package org.gradle.integtests.fixtures

import org.jspecify.annotations.NullMarked
import org.junit.runners.model.Statement

import static org.junit.Assume.assumeTrue

/**
 * Skips a JUnit test that is unsupported under the active Gradle mode.
 */
@NullMarked
class SkippingRuleStatement extends Statement {

    private final String gradleMode
    private final String reason

    SkippingRuleStatement(String gradleMode, String reason) {
        this.gradleMode = gradleMode
        this.reason = reason
    }

    @Override
    void evaluate() throws Throwable {
        String message = reason == null || reason.isEmpty()
            ? "Test does not support ${gradleMode}"
            : "Test does not support ${gradleMode}: $reason"
        assumeTrue(message, false)
    }
}
