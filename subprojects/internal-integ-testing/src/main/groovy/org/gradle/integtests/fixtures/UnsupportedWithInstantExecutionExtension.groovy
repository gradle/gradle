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
import org.junit.AssumptionViolatedException
import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecInfo

import static org.gradle.integtests.fixtures.ToBeFixedForInstantExecutionExtension.iterationMatches
import static org.gradle.integtests.fixtures.ToBeFixedForInstantExecutionExtension.isAllIterations
import static org.gradle.integtests.fixtures.ToBeFixedForInstantExecutionExtension.isEnabledBottomSpec


class UnsupportedWithInstantExecutionExtension extends AbstractAnnotationDrivenExtension<UnsupportedWithInstantExecution> {

    @Override
    void visitSpecAnnotation(UnsupportedWithInstantExecution annotation, SpecInfo spec) {
        if (GradleContextualExecuter.isInstant()) {
            if (isAllIterations(annotation.iterationMatchers()) && isEnabledBottomSpec(annotation.bottomSpecs(), { spec.bottomSpec.name == it })) {
                spec.skipped = true
            } else {
                spec.features.each { feature ->
                    feature.iterationInterceptors.add(new IterationMatchingMethodInterceptor(annotation.iterationMatchers()))
                }
            }
        }
    }

    @Override
    void visitFeatureAnnotation(UnsupportedWithInstantExecution annotation, FeatureInfo feature) {
        if (GradleContextualExecuter.isInstant()) {
            if (isAllIterations(annotation.iterationMatchers()) && isEnabledBottomSpec(annotation.bottomSpecs(), { feature.parent.bottomSpec.name == it })) {
                feature.skipped = true
            } else {
                feature.iterationInterceptors.add(new IterationMatchingMethodInterceptor(annotation.iterationMatchers()))
            }
        }
    }

    private static class IterationMatchingMethodInterceptor implements IMethodInterceptor {

        private final String[] iterationMatchers

        IterationMatchingMethodInterceptor(String[] iterationMatchers) {
            this.iterationMatchers = iterationMatchers
        }

        @Override
        void intercept(IMethodInvocation invocation) throws Throwable {
            if (!iterationMatches(iterationMatchers, invocation.iteration.name)) {
                invocation.proceed()
            } else {
                throw new AssumptionViolatedException("Unsupported with instant execution")
            }
        }
    }
}
