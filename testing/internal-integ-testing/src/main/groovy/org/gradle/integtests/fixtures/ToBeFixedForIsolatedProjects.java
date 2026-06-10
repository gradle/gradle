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

package org.gradle.integtests.fixtures;

import org.spockframework.runtime.extension.ExtensionAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Expect the test to fail or skip it when running with Isolated Projects executor.
 * <p>
 * Use this annotation when the intention is to fix either the test itself or the underlying feature,
 * making it compatible with Isolated Projects. If the intention is to not support the tested feature
 * with Isolated Projects, use {@link UnsupportedWithIsolatedProjects} instead.
 * <p>
 * The expectation of failure essentially flips the test result.
 * A specific failure is not verified, and we only confirm that the test is not passing.
 * <p>
 * Instead of expecting failure, you can skip the test in case the test doesn't fail consistently
 * or has other undesirable effects, such as timeouts.
 * Set {@link #skip()} into any other value apart from {@link Skip#DO_NOT_SKIP DO_NOT_SKIP} to skip the test.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ExtensionAnnotation(GradleModeTestingExtensions.ToBeFixedForIP.class)
@GradleModeTestingIntent(mode = GradleModeTesting.ISOLATED_PROJECTS, kind = GradleModeTestingIntent.Kind.TO_BE_FIXED)
public @interface ToBeFixedForIsolatedProjects {

    /**
     * Set to some {@link Skip} to skip the annotated test.
     */
    Skip skip() default Skip.DO_NOT_SKIP;

    /**
     * Declare to which bottom spec this annotation should be applied.
     * Defaults to an empty array, meaning this annotation applies to all bottom specs.
     */
    String[] bottomSpecs() default {};

    /**
     * Declare regular expressions matching the iteration name.
     * Defaults to an empty array, meaning this annotation applies to all iterations of the annotated feature.
     */
    String[] iterationMatchers() default {};

    String because() default "";

    /**
     * Link to the issue tracking the incompatibility addressed by this annotation.
     * Distinct from {@code @spock.lang.Issue}, which links the test itself to its tracking issue.
     */
    String issue() default "";

    /**
     * Reason for skipping a test with isolated projects.
     */
    enum Skip {

        /**
         * Do not skip this test, this is the default.
         */
        DO_NOT_SKIP {
            @Override
            public String getReason() {
                throw new UnsupportedOperationException("Must not be skipped");
            }
        },

        /**
         * Use this reason on tests that intermittently fail with isolated projects.
         */
        FLAKY {
            @Override
            public String getReason() {
                return "flaky";
            }
        };

        public abstract String getReason();
    }
}
