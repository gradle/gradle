/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.internal.typeconversion.TypeConversionException;

import java.util.List;
import java.util.Set;

public interface OptionElement {
    Class<?> getDeclaredClass();

    Set<String> getAvailableValues();

    Class<?> getOptionType();

    String getElementName();

    String getOptionName();

    /**
     * @throws TypeConversionException On failure to convert the supplied values to the appropriate target types.
     */
    void apply(Object object, List<String> parameterValues) throws TypeConversionException;

    String getDescription();

    int getOrder();
}
