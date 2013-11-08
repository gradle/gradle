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

import org.gradle.api.internal.coerce.EnumFromStringNotationParser;
import org.gradle.api.internal.notations.api.NotationParser;

import java.util.ArrayList;
import java.util.List;

abstract class AbstractOptionElement implements OptionElement {

    protected Object getParameterObject(String value) {
        if (getOptionType().isEnum()) {
            NotationParser parser = new EnumFromStringNotationParser(getOptionType());
            return parser.parseNotation(value);
        }
        return value;
    }

    protected Class<?> calculateOptionType(Class<?> type) {
        //we don't want to support "--flag true" syntax
        if (type == Boolean.class || type == Boolean.TYPE) {
            return Void.TYPE;
        } else {
            return type;
        }
    }

    protected List<String> calculdateAvailableValues(Class<?> type) {
        List<String> availableValues = new ArrayList<String>();
        if (type.isEnum()) {
            final Enum[] enumConstants = (Enum[]) type.getEnumConstants();
            for (Enum enumConstant : enumConstants) {
                availableValues.add(enumConstant.name());
            }
        }
        return availableValues;
    }
}
