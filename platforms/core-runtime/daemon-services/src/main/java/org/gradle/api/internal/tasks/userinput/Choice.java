/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.userinput;

import java.util.function.Function;

/**
 * Asks the user to select an option from a list of options. Allows the choice to be configured in various ways.
 *
 * @param <T> The type of the options of this choice.
 */
public interface Choice<T> {
    /**
     * Specifies the option to present to the user as the default selection, and the option to use when not connected to a console.
     * Both of these values default to the first option.
     *
     * <p>Replaces any value set using {@link #whenNotConnected(Object)}.
     *
     * @return this
     */
    Choice<T> defaultOption(T defaultOption);

    /**
     * Specifies the option to use when not connected to a console. This can be different to the default option presented to the user.
     *
     * @return this
     */
    Choice<T> whenNotConnected(T defaultOption);

    /**
     * Specifies how to display each option.
     *
     * @return this
     */
    Choice<T> renderUsing(Function<T, String> renderer);

    /**
     * Prompts the user to select an option.
     */
    T ask();
}
