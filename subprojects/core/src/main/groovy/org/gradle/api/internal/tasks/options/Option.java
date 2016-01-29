/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks.options;

import java.lang.annotation.*;

/**
 * Marks a property as available from the command-line.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@Inherited
public @interface Option {
    /**
     * The option to map to this property.
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

    /**
     * The order of this option for displaying in help command output. Allows overriding default alphabetical order.
     *
     * @return The order.
     */
    int order() default 0;
}
