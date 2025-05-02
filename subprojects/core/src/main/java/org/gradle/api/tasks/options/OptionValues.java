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
 * <p>Marks a method on a {@link org.gradle.api.Task} as providing the possible values for a {@code String}
 * or {@code List<String>} {@link Option}. At most one option values method may be provided for a
 * particular option.</p>
 *
 * <p>This annotation should be attached to a getter method that returns a {@link java.util.Collection} of
 * possible values. The entries in the collection may be of any type. If necessary, they are transformed
 * into {@link String Strings} by calling {@code toString()}.</p>
 *
 * @since 4.6
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Inherited
public @interface OptionValues {

    /**
     * The names of the options for which the method provides the possible values.
     * @return the option names
     */
    String[] value();
}
