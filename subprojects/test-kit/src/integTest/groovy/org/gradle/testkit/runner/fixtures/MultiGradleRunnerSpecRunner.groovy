/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner.fixtures

import org.gradle.integtests.fixtures.AbstractMultiTestRunner
import org.gradle.integtests.fixtures.TargetCoverage

class MultiGradleRunnerSpecRunner extends AbstractMultiTestRunner {
    MultiGradleRunnerSpecRunner(Class<?> target) {
        super(target)
    }

    @Override
    protected void createExecutions() {
        def coverage = target.getAnnotation(TargetCoverage)
        def coverageTargets = determineCoverageTargets(coverage)

        coverageTargets.each { GradleRunnerType coverageTarget ->
            add(new GradleRunnerExecution(coverageTarget))
        }
    }

    private EnumSet determineCoverageTargets(coverage) {
        if (coverage != null) {
            return coverage.value().newInstance(target, target).call()
        }

        return GradleRunnerCoverage.ALL
    }

    private static class GradleRunnerExecution extends AbstractMultiTestRunner.Execution {
        private final GradleRunnerType gradleRunnerType

        GradleRunnerExecution(GradleRunnerType gradleRunnerType) {
            this.gradleRunnerType = gradleRunnerType
        }

        @Override
        protected String getDisplayName() {
            gradleRunnerType.displayName
        }

        @Override
        protected void before() {
            target.gradleRunnerType = gradleRunnerType
        }

        @Override
        protected boolean isTestEnabled(AbstractMultiTestRunner.TestDetails testDetails) {
            IgnoreTarget ignoreTarget = testDetails.getAnnotation(IgnoreTarget)

            if (ignoreTarget) {
                EnumSet enumSet = ignoreTarget.value().newInstance(target, target).call()
                return !enumSet.contains(gradleRunnerType)
            }

            true
        }
    }
}
