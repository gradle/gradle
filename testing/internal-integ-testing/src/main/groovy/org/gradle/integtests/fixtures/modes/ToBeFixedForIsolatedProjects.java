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

package org.gradle.integtests.fixtures.modes;

import org.spockframework.runtime.extension.ExtensionAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Expect the test to fail or skip it when running with Isolated Projects enabled.
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
 * Set {@link #skipBecause()} to a non-empty reason to skip the test.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@ExtensionAnnotation(ToBeFixedForIsolatedProjectsExtension.class)
public @interface ToBeFixedForIsolatedProjects {

    String because() default "";

    /**
     * Reason for skipping the annotated test instead of expecting it to fail.
     * Empty (the default) means expect-failure; any non-empty value skips the test with that reason.
     */
    String skipBecause() default "";

    /**
     * Link to the issue tracking the incompatibility addressed by this annotation.
     * Distinct from {@code @spock.lang.Issue}, which links the test itself to its tracking issue.
     */
    String issue() default "";

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
}
