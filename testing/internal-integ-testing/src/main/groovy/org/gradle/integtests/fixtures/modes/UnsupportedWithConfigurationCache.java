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

/// Skip this test (or all tests in this spec) under Configuration Cache: the feature is not meant to be supported.
///
/// Use [ToBeFixedForConfigurationCache] when the test or feature is expected to be made compatible eventually.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ExtensionAnnotation(GradleModeTestingExtension.UnsupportedWithCC.class)
public @interface UnsupportedWithConfigurationCache {

    /// Why this feature is not supported with Configuration Cache.
    String because() default "";

    /// Link to the issue documenting non-support. Distinct from `@spock.lang.Issue`.
    String issue() default "";

    /// Limit to specific leaf specs by simple class name. Empty means all subclasses.
    String[] bottomSpecs() default {};

    /// Regexes matched against parameterized iteration display names. Empty means all iterations.
    String[] iterationMatchers() default {};
}
