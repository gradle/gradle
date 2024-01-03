/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model;

import org.gradle.api.Incubating;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Denotes that the {@link RuleSource} method rule carrying this annotation creates a new top level element in the model space.
 * <p>
 * The method must advertise a name and type for the model element.
 * The name is defined either by the name of the method, or the {@link #value} of this annotation.
 * The type is defined differently depending on whether the new element is {@link Managed} or not.

 * <h3>Creating managed model elements</h3>
 * <p>
 * If the element is to be of a managed type, the method must return {@code void} and receive the newly created instance as the <b>first</b> parameter.
 * All other parameters are considered <i>inputs</i>.
 * <p>
 * It is an error for a {@code @Model} rule to return {@code void} and specify a non-managed type as the first parameter.
 * It is an error for a {@code @Model} rule to return {@code void} and for the first parameter to be annotated with {@link Path}.
 * It is an error for a {@code @Model} rule to specify a managed type as the return type.

 * <h3>Creating non-managed model elements</h3>
 * <p>
 * If the element is to be of a non-managed type, the method must return the newly created instance.
 * All parameters are considered <i>inputs</i>.
 *
 * Please see {@link RuleSource} for more information on method rules.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Incubating
public @interface Model {

    /**
     * Denotes the name by which the model element will be available.
     * <p>
     * If the value is the empty string, the exact name of the annotated method will be used.
     * <p>
     * The value must:
     * <ul>
     * <li>Start with a lower case letter</li>
     * <li>Contain only ASCII letters, numbers and the '_' character</li>
     * </ul>
     * <p>
     * This restriction also applies when the name is being derived from the method name.
     * </p>
     *
     * @return the name by which the model element will be available
     */
    String value() default "";

}
