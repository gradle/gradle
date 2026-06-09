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
import org.jspecify.annotations.Nullable
import org.opentest4j.TestAbortedException
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecElementInfo
import org.spockframework.runtime.model.SpecInfo

import java.lang.annotation.Annotation

import static java.util.Objects.requireNonNull

/**
 * Spock extensions that gate Spock specs and features by the active {@link GradleModeTesting}.
 */
@NullMarked
final class GradleModeTestingExtensions {

    private GradleModeTestingExtensions() {}

    /**
     * Applies a {@link GradleModeTestingPolicy}'s verdict to a Spock spec or feature.
     */
    @NullMarked
    static abstract class BaseExtension<A extends Annotation> implements IAnnotationDrivenExtension<A> {

        private final GradleModeTestingPolicy<A> policy
        @Nullable
        private final ToBeFixedSpecInterceptor expectFailureInterceptor

        BaseExtension(GradleModeTestingPolicy<A> policy, ToBeFixedSpecInterceptor expectFailureInterceptor) {
            this.policy = policy
            this.expectFailureInterceptor = expectFailureInterceptor
        }

        @Override
        void visitSpecAnnotation(A annotation, SpecInfo spec) {
            GradleModeTestingIntentValidator.validateSpec(spec.bottomSpec.reflection)
            apply(annotation, spec, spec.bottomSpec.name)
        }

        @Override
        void visitFeatureAnnotation(A annotation, FeatureInfo feature) {
            GradleModeTestingIntentValidator.validateFeature(
                feature.spec.bottomSpec.reflection,
                feature.featureMethod.reflection
            )
            apply(annotation, feature, feature.spec.bottomSpec.name)
        }

        private void apply(A annotation, SpecElementInfo specOrFeature, String bottomSpecName) {
            switch (policy.decide(annotation, bottomSpecName, null)) {
                case GradleModeTestingPolicy.Verdict.SKIP:
                    skip(specOrFeature, policy.skipReason(annotation))
                    return
                case GradleModeTestingPolicy.Verdict.EXPECT_FAILURE:
                    requireNonNull(expectFailureInterceptor, "Policy returned EXPECT_FAILURE but no expect-failure interceptor was provided to ${this.class.simpleName}")
                        .intercept(specOrFeature, policy.iterationMatchers(annotation))
                    return
                case GradleModeTestingPolicy.Verdict.RUN:
                    if (policy.requiresPerIterationCheck(annotation, bottomSpecName)) {
                        if (expectFailureInterceptor != null) {
                            expectFailureInterceptor.intercept(specOrFeature, policy.iterationMatchers(annotation))
                        } else {
                            addPerIterationInterceptor(specOrFeature, perIterationSkipInterceptor(annotation, bottomSpecName))
                        }
                    }
            }
        }

        private IMethodInterceptor perIterationSkipInterceptor(A annotation, String bottomSpecName) {
            return { invocation ->
                if (policy.decide(annotation, bottomSpecName, invocation.iteration.displayName) == GradleModeTestingPolicy.Verdict.SKIP) {
                    String reason = policy.skipReason(annotation)
                    throw new TestAbortedException(reason ? "Unsupported with ${policy.gradleMode()}: $reason" : "Unsupported with ${policy.gradleMode()}")
                }
                invocation.proceed()
            } as IMethodInterceptor
        }

        private static void addPerIterationInterceptor(SpecElementInfo specOrFeature, IMethodInterceptor interceptor) {
            if (specOrFeature instanceof SpecInfo) {
                specOrFeature.features.each { it.featureMethod.interceptors.add(0, interceptor) }
            } else {
                ((FeatureInfo) specOrFeature).featureMethod.interceptors.add(0, interceptor)
            }
        }

        private static void skip(SpecElementInfo specOrFeature, String reason) {
            if (reason.isEmpty()) {
                specOrFeature.skipped = true
            } else {
                specOrFeature.skip(reason)
            }
        }
    }

    static class ToBeFixedForCC extends BaseExtension<ToBeFixedForConfigurationCache> {
        ToBeFixedForCC() {
            super(
                new GradleModeTestingPolicy.ToBeFixedForCC(),
                new ToBeFixedSpecInterceptor(GradleModeTesting.CONFIGURATION_CACHE.displayName())
            )
        }
    }

    static class ToBeFixedForIP extends BaseExtension<ToBeFixedForIsolatedProjects> {
        ToBeFixedForIP() {
            super(
                new GradleModeTestingPolicy.ToBeFixedForIP(),
                new ToBeFixedSpecInterceptor(GradleModeTesting.ISOLATED_PROJECTS.displayName())
            )
        }
    }

    static class UnsupportedWithCC extends BaseExtension<UnsupportedWithConfigurationCache> {
        UnsupportedWithCC() {
            super(new GradleModeTestingPolicy.UnsupportedWithCC(), null)
        }
    }

    static class UnsupportedWithIP extends BaseExtension<UnsupportedWithIsolatedProjects> {
        UnsupportedWithIP() {
            super(new GradleModeTestingPolicy.UnsupportedWithIP(), null)
        }
    }
}
