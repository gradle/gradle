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
 * Assert that this test fails when run with Isolated Projects enabled.
 * <p>
 * In case the {@link #skip()} reason is anything but {@link Skip#DO_NOT_SKIP DO_NOT_SKIP}, the test will be skipped.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@ExtensionAnnotation(GradleModeTestingExtensions.ToBeFixedForIP.class)
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
