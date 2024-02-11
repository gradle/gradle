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

import org.gradle.integtests.fixtures.executer.ConfigurationCacheGradleExecuter;
import org.spockframework.runtime.extension.ExtensionAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Denotes a test for a feature that is unsupported with configuration cache.
 * Do not use this for tests for features that are supported by configuration cache but where the test happens to be incompatible.
 *
 * <p>The annotated test will be skipped by the {@link ConfigurationCacheGradleExecuter}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ExtensionAnnotation(UnsupportedWithConfigurationCacheExtension.class)
public @interface UnsupportedWithConfigurationCache {

    String because() default "";

    String[] bottomSpecs() default {};

    /**
     * Declare regular expressions matching the iteration name.
     * Defaults to an empty array, meaning this annotation applies to all iterations of the annotated feature.
     */
    String[] iterationMatchers() default {};
}
