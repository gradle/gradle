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

import org.apache.commons.lang.StringUtils;
import org.gradle.internal.typeconversion.ValueAwareNotationParser;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class FieldOptionElement extends AbstractOptionElement {

    public static FieldOptionElement create(Option option, Field field, OptionNotationParserFactory optionNotationParserFactory){
        String optionName = calOptionName(option, field);
        Class<?> optionType = calculateOptionType(field.getType());
        ValueAwareNotationParser<?> notationParser = createNotationParserOrFail(optionNotationParserFactory, optionName, optionType, field.getDeclaringClass());
        return new FieldOptionElement(field, optionName, option, optionType, notationParser);
    }

    private final Field field;

    public FieldOptionElement(Field field, String optionName, Option option, Class<?> optionType, ValueAwareNotationParser<?> notationParser) {
        super(optionName, option, optionType, field.getDeclaringClass(), notationParser);
        this.field = field;
        getSetter();
    }

    private static String calOptionName(Option option, Field field) {
        if (option.option().length() == 0) {
            return field.getName();
        } else {
            return option.option();
        }
    }

    private Method getSetter() {
        try{
            String setterName = "set" + StringUtils.capitalize(field.getName());
            return field.getDeclaringClass().getMethod(setterName, field.getType());
        } catch (NoSuchMethodException e) {
            throw new OptionValidationException(String.format("No setter for Option annotated field '%s' in class '%s'.",
                    getElementName(), getDeclaredClass()));
        }
    }

    public String getElementName() {
        return field.getName();
    }

    public Class<?> getDeclaredClass() {
        return field.getDeclaringClass();
    }

    public void apply(Object object, List<String> parameterValues) {
        if (getOptionType() == Void.TYPE && parameterValues.size() == 0) {
            setFieldValue(object, true);
        } else if (parameterValues.size() > 1) {
            throw new IllegalArgumentException(String.format("Lists not supported for option"));
        } else {
            Object arg = getNotationParser().parseNotation(parameterValues.get(0));
            setFieldValue(object, arg);
        }
    }

    private void setFieldValue(Object object, Object value) {
            Method setter = getSetter();
            invokeMethod(object, setter, value);
    }
}

