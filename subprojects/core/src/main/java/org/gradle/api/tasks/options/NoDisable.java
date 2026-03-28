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
package org.gradle.api.tasks.options;

import org.gradle.api.Incubating;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Modifier for {@link Option} that suppresses the automatic generation of the negative
 * counterpart option.
 *
 * <p>By default, every boolean {@link Option} gets an opposite option generated automatically.
 * For example, an option {@code --foo} gets a {@code --no-foo} counterpart that sets the value to false.
 * Annotating the same element with {@code @NoDisable} prevents that counterpart from being generated,
 * so only the positive flag ({@code --foo}) is available on the command line.</p>
 *
 * <p>This annotation must be used alongside {@link Option} on a boolean option (i.e. a {@code boolean},
 * {@code Boolean}, or {@code Property<Boolean>} type). Using it on a non-boolean option is an error.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * {@literal @}Option(option = "allow-no-matches", description = "Allow the task to succeed when no tests match the filter.")
 * {@literal @}NoDisable
 * public abstract Property&lt;Boolean&gt; getAllowNoMatchingTests();
 * </pre>
 *
 * @since 9.6.0
 * @see Option
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@Inherited
@Incubating
public @interface NoDisable {
}
