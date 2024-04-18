/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.test.precondition;

import org.spockframework.runtime.extension.ExtensionAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation defining a set of requirements.
 * These requirements should be met per test-class or method, or else the test is ignored.
 *
 * <p>
 * Combinations are pre-defined. See {@link RequiresExtension} for more info how to introduce a new combination.
 *
 * @see RequiresExtension
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Inherited
@ExtensionAnnotation(RequiresExtension.class)
public @interface Requires {

    /**
     * The list of preconditions, which will be checked by {@link RequiresExtension}
     */
    Class<? extends TestPrecondition>[] value();

    String reason() default "";
}
