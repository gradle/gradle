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
import org.opentest4j.TestAbortedException
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecInfo

import static ToBeFixedSpecInterceptor.isAllIterations
import static org.gradle.integtests.fixtures.ToBeFixedForConfigurationCacheExtension.isEnabledBottomSpec
import static ToBeFixedSpecInterceptor.iterationMatches

class UnsupportedWithConfigurationCacheExtension implements IAnnotationDrivenExtension<UnsupportedWithConfigurationCache> {

    @Override
    void visitSpecAnnotation(UnsupportedWithConfigurationCache annotation, SpecInfo spec) {
        if (GradleContextualExecuter.isConfigCache()) {
            if (isAllIterations(annotation.iterationMatchers()) && isEnabledBottomSpec(annotation.bottomSpecs(), { spec.bottomSpec.name == it })) {
                spec.skipped = true
            } else {
                spec.features.each { feature ->
                    feature.getFeatureMethod().addInterceptor(new IterationMatchingMethodInterceptor(annotation.iterationMatchers()))
                }
            }
        }
    }

    @Override
    void visitFeatureAnnotation(UnsupportedWithConfigurationCache annotation, FeatureInfo feature) {
        if (GradleContextualExecuter.isConfigCache()) {
            if (isAllIterations(annotation.iterationMatchers()) && isEnabledBottomSpec(annotation.bottomSpecs(), { feature.parent.bottomSpec.name == it })) {
                feature.skipped = true
            } else {
                feature.getFeatureMethod().addInterceptor(new IterationMatchingMethodInterceptor(annotation.iterationMatchers()))
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
            if (iterationMatches(iterationMatchers, invocation.iteration.displayName)) {
                throw new TestAbortedException("Unsupported with configuration cache")
            }
            invocation.proceed()
        }
    }
}
