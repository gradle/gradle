/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.test.precondition

import org.jetbrains.annotations.NotNull
import org.junit.Assume
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class PreconditionVerifier implements TestRule {
    @Override
    Statement apply(@NotNull Statement base, @NotNull Description description) {
        List<Class<? extends TestPrecondition>> preconditions = description.annotations.findAll { it instanceof Requires }*.value().flatten()
        PredicatesFile.checkValidCombinations(preconditions, PredicatesFile.DEFAULT_ACCEPTED_COMBINATIONS)
        TestPrecondition.allSatisfied(preconditions.toArray(new Class<? extends TestPrecondition>[0])) ? base : new IgnoreStatement(preconditions)
    }

    private static class IgnoreStatement extends Statement {
        private final List<Class<? extends TestPrecondition>> preconditions

        IgnoreStatement(List<Class<? extends TestPrecondition>> preconditions) {
            this.preconditions = preconditions
        }

        @Override
        void evaluate() {
            Assume.assumeTrue("Not all Requirements ${preconditions*.simpleName} are satisfied", false)
        }
    }
}
