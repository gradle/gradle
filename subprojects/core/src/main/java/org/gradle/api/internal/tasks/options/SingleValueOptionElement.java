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
import org.gradle.internal.exceptions.ValueCollectingDiagnosticsVisitor;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.TypeConversionException;

import java.util.List;
import java.util.Set;

/**
 * An option with a single argument.
 */
public class SingleValueOptionElement extends AbstractOptionElement {
    private final PropertySetter setter;
    private final NotationParser<CharSequence, ?> notationParser;

    public SingleValueOptionElement(String optionName, Option option, Class<?> optionType, PropertySetter setter, OptionValueNotationParserFactory notationParserFactory) {
        super(optionName, option, String.class, setter.getDeclaringClass());
        this.setter = setter;
        notationParser = createNotationParserOrFail(notationParserFactory, optionName, optionType, setter.getDeclaringClass());
    }

    @Override
    public Set<String> getAvailableValues() {
        ValueCollectingDiagnosticsVisitor visitor = new ValueCollectingDiagnosticsVisitor();
        notationParser.describe(visitor);
        return visitor.getValues();
    }

    @Override
    public void apply(Object object, List<String> parameterValues) throws TypeConversionException {
        Object arg = notationParser.parseNotation(parameterValues.get(0));
        setter.setValue(object, arg);
    }
}


