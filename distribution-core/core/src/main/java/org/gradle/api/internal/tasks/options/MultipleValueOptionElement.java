/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.tasks.options.Option;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.TypeConversionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * An option with one or more values.
 */
public class MultipleValueOptionElement extends AbstractOptionElement {
    private final PropertySetter setter;
    private final NotationParser<CharSequence, ?> notationParser;

    public MultipleValueOptionElement(String optionName, Option option, Class<?> elementType, PropertySetter setter, OptionValueNotationParserFactory notationParserFactory) {
        super(optionName, option, List.class, setter.getDeclaringClass());
        this.setter = setter;
        this.notationParser = createNotationParserOrFail(notationParserFactory, optionName, elementType, setter.getDeclaringClass());
    }

    @Override
    public Set<String> getAvailableValues() {
        return Collections.emptySet();
    }

    @Override
    public void apply(Object object, List<String> parameterValues) throws TypeConversionException {
        List<Object> values = new ArrayList<Object>(parameterValues.size());
        for (String parameterValue : parameterValues) {
            values.add(notationParser.parseNotation(parameterValue));
        }
        setter.setValue(object, values);
    }
}
