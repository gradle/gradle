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

public interface OptionDescriptor extends Comparable<OptionDescriptor> {

    String getName();

    Class<?> getArgumentType();

    Set<String> getAvailableValues();

    String getDescription();

    /**
     * An option will be considered clashing (and hence can be ignored/reported)
     * if it has the same name as a previous option.
     */
    boolean isClashing();

    /**
     * @throws TypeConversionException On failure to convert the given values to the required types.
     */
    void apply(Object object, List<String> values) throws TypeConversionException;
}

