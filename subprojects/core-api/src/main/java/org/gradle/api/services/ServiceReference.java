/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.services;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Optional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Marks a task property as being a holder to a {@link BuildService}.</p>
 *
 * <p>
 * When you annotate a shared build service property with this annotation,
 * there is no need to explicitly declare the association between the task and the service;
 * also, if you provide a service name to the annotation, and a shared build service is
 * registered with that name, it will be automatically assigned to the property when the
 * task is created.
 * </p>
 * <p>
 * It is an error to apply this annotation to a property whose type is not a subtype of {@link BuildService}.
 * </p>
 *
 * @since 8.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface ServiceReference {
    /**
     * <p>The optional name of the service which the annotated element references.</p>
     *
     * <p>
     * In case a service name is provided, if a shared build service is registered with that name,
     * a provider to the service will be automatically assigned to the property.
     * </p>
     * <p>
     * If a shared build service with the specified name is not found, and no value or convention
     * is explicitly set on the property:
     * </p>
     * <ul>
     * <li>if the property is optional, an exception will only occur if an attempt is made to obtain the value of the property (see {@link Property#get()});</li>
     * <li>if the property is mandatory, before the task starts executing, a validation error will be issued.</li>
     * </ul>
     *
     * @see Optional
     * @see Property#get()
     */
    String value() default "";
}
