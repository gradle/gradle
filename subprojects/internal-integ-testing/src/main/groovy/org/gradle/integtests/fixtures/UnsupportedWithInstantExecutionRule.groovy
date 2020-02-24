/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

import static org.gradle.integtests.fixtures.ToBeFixedForInstantExecutionExtension.iterationMatches
import static org.gradle.integtests.fixtures.ToBeFixedForInstantExecutionExtension.isEnabledBottomSpec
import static org.junit.Assume.assumeFalse


class UnsupportedWithInstantExecutionRule implements TestRule {

    @Override
    Statement apply(Statement base, Description description) {
        def annotation = description.getAnnotation(UnsupportedWithInstantExecution.class)
        if (GradleContextualExecuter.isNotInstant() || annotation == null) {
            return base
        }
        def enabledBottomSpec = isEnabledBottomSpec(annotation.bottomSpecs(), { description.className.endsWith(".$it") })
        def enabledIteration = iterationMatches(annotation.iterationMatchers(), description.methodName)
        if (enabledBottomSpec && enabledIteration) {
            return new SkippingRuleStatement(base)
        }
        return base
    }

    static class SkippingRuleStatement extends Statement {

        private final Statement next

        SkippingRuleStatement(Statement next) {
            this.next = next
        }

        @Override
        void evaluate() throws Throwable {
            assumeFalse(GradleContextualExecuter.isInstant())
            next.evaluate()
        }
    }
}
