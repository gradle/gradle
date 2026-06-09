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

package org.gradle.integtests.fixtures;

import org.spockframework.runtime.extension.ExtensionAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Skip the test when running with Isolated Projects executor.
 * <p>
 * Use this annotation when the tested feature is fundamentally incompatible with Isolated Projects
 * and there is no intention to support it. If the intention is to eventually fix either the test
 * or the underlying feature, use {@link ToBeFixedForIsolatedProjects} instead.
 * <p>
 * The annotated test is skipped entirely; no assertion is made about its behavior under Isolated Projects.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ExtensionAnnotation(GradleModeTestingExtensions.UnsupportedWithIP.class)
@GradleModeTestingIntent(mode = GradleModeTesting.ISOLATED_PROJECTS, kind = GradleModeTestingIntent.Kind.WONT_SUPPORT)
public @interface UnsupportedWithIsolatedProjects {

    String because() default "";

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
