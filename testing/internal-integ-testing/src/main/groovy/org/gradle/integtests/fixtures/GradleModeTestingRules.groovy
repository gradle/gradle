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
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

import java.lang.annotation.Annotation

/**
 * JUnit rules that gate plain JUnit tests by the active Gradle mode,
 * mirroring {@link GradleModeTestingExtensions} for Spock.
 */
@NullMarked
final class GradleModeTestingRules {

    private GradleModeTestingRules() {}

    private static abstract class BaseRule<A extends Annotation> implements TestRule {

        private final GradleModeTestingPolicy<A> policy
        private final Class<A> annotationType

        protected BaseRule(GradleModeTestingPolicy<A> policy, Class<A> annotationType) {
            this.policy = policy
            this.annotationType = annotationType
        }

        @Override
        Statement apply(Statement base, Description description) {
            if (!description.isSuite() && description.testClass != null && description.testClass.getAnnotation(annotationType) != null) {
                throw new IllegalStateException(
                    "@${annotationType.simpleName} on JUnit class ${description.testClass.name} has no effect: " +
                        "class-level Gradle-mode gating is only supported for Spock specs. " +
                        "Apply the annotation to each affected test method instead."
                )
            }

            A annotation = description.getAnnotation(annotationType)
            if (annotation == null) {
                return base
            }
            GradleModeTestingPolicy.Verdict verdict = policy.decide(annotation, description.className, description.methodName)
            switch (verdict) {
                case GradleModeTestingPolicy.Verdict.SKIP:
                    return new SkippingRuleStatement(policy.gradleMode(), policy.skipReason(annotation))
                case GradleModeTestingPolicy.Verdict.EXPECT_FAILURE:
                    return new ExpectingFailureRuleStatement(base, policy.gradleMode())
                case GradleModeTestingPolicy.Verdict.RUN:
                    return base
            }
            throw new IllegalStateException("Unhandled verdict: $verdict")
        }
    }

    static class ToBeFixedForCC extends BaseRule<ToBeFixedForConfigurationCache> {
        ToBeFixedForCC() {
            super(new GradleModeTestingPolicy.ToBeFixedForCC(), ToBeFixedForConfigurationCache.class)
        }
    }

    static class ToBeFixedForIP extends BaseRule<ToBeFixedForIsolatedProjects> {
        ToBeFixedForIP() {
            super(new GradleModeTestingPolicy.ToBeFixedForIP(), ToBeFixedForIsolatedProjects.class)
        }
    }

    static class UnsupportedWithCC extends BaseRule<UnsupportedWithConfigurationCache> {
        UnsupportedWithCC() {
            super(new GradleModeTestingPolicy.UnsupportedWithCC(), UnsupportedWithConfigurationCache.class)
        }
    }

    static class UnsupportedWithIP extends BaseRule<UnsupportedWithIsolatedProjects> {
        UnsupportedWithIP() {
            super(new GradleModeTestingPolicy.UnsupportedWithIP(), UnsupportedWithIsolatedProjects.class)
        }
    }
}
