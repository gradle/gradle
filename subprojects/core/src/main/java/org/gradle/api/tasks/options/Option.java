/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.tasks.options;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Marks a property of a {@link org.gradle.api.Task} as being configurable from the command-line.</p>
 *
 * <p>This annotation should be attached to a field or a setter method. When attached to a field, {@link #option()}
 * will use the name of the field by default. When attached to a method, {@link #option()} must be specified.</p>
 *
 * <p>An option may have one of the following types:</p>
 * <ul>
 * <li>{@code boolean}</li>
 * <li>{@code Boolean}</li>
 * <li>{@code Property<Boolean>}</li>
 * <li>an {@code enum} type</li>
 * <li>{@code Property<T>} of an enum type</li>
 * <li>{@code String}</li>
 * <li>{@code Property<String>}</li>
 * <li>{@code List<T>} of an {@code enum} type</li>
 * <li>{@code List<String>}</li>
 * </ul>
 *
 * @since 4.6
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@Inherited
public @interface Option {

    /**
     * The option to map to this property. Required when annotating a method. May be omitted when annotating a field
     * in which case the field's name will be used.
     *
     * @return The option.
     */
    String option() default "";

    /**
     * The description of this option.
     *
     * @return The description.
     */
    String description();
}
