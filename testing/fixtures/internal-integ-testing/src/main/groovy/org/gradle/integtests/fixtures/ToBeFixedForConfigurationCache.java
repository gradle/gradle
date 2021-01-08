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

package org.gradle.integtests.fixtures;

import org.spockframework.runtime.extension.ExtensionAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Assert that this test fails when run with configuration cache enabled.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ExtensionAnnotation(ToBeFixedForConfigurationCacheExtension.class)
public @interface ToBeFixedForConfigurationCache {

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
     * Reason for skipping a test with configuration cache.
     */
    enum Skip {

        /**
         * Do not skip this test, this is the default.
         */
        DO_NOT_SKIP,

        /**
         * Use this reason on unrolled tests in super classes that fail on some subclasses.
         * Spock doesn't allow to override test methods and annotate them.
         */
        UNROLLED_FAILS_IN_SUBCLASS,

        /**
         * Use this reason on tests that fail <code>:verifyTestFilesCleanup</code> with configuration cache.
         */
        FAILS_TO_CLEANUP,

        /**
         * Use this reason on tests that intermittently fail with configuration cache.
         */
        FLAKY,

        /**
         * Use this reason on tests that take a long time to fail, slowing down the CI feedback.
         * Use sparingly, only in dramatic cases.
         */
        LONG_TIMEOUT
    }
}
