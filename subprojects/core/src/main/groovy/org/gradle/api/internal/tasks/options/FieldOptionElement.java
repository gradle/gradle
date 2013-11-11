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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class FieldOptionElement extends AbstractOptionElement {

    private final Field field;
    private List<String> availableValues;
    private Class<?> optionType;

    public FieldOptionElement(Option option, Field field) {
        super(calOptionName(option, field), option, field.getDeclaringClass());
        assertFieldSupported(field);
        this.field = field;
        this.optionType = calculateOptionType(field.getType());
    }

    private static String calOptionName(Option option, Field field) {
        if (option.option().length() == 0) {
            return field.getName();
        } else {
            return option.option();
        }
    }

    private void assertFieldSupported(Field field) {
        final Class<?> type = field.getType();
        if (!(type == Boolean.class || type == Boolean.TYPE)
                && !type.isAssignableFrom(String.class)
                && !type.isEnum()) {
            throw new OptionValidationException(String.format("Option '%s' cannot be casted to type '%s' in class '%s'.",
                    getOptionName(), type.getName(), field.getDeclaringClass().getName()));
        }
    }

    public Class<?> getOptionType() {
        return optionType;
    }

    public String getElementName() {
        return field.getName();
    }

    public Class<?> getDeclaredClass() {
        return field.getDeclaringClass();
    }

    public List<String> getAvailableValues() {
        //calculate list lazy to avoid overhead upfront
        if (availableValues == null) {
            availableValues = calculdateAvailableValues(field.getType());
        }
        return availableValues;
    }


    public void apply(Object object, List<String> parameterValues) {
        if (optionType == Void.TYPE && parameterValues.size() == 0) {
            setFieldValue(object, true);
        } else if (parameterValues.size() > 1) {
            throw new IllegalArgumentException(String.format("Lists not supported for option"));
        } else {
            Object arg = getParameterObject(parameterValues.get(0));
            setFieldValue(object, arg);
        }
    }

    private void setFieldValue(Object object, Object value) {
        try {
            Method setter = object.getClass().getMethod("set" + StringUtils.capitalize(field.getName()), optionType);
            invokeMethod(object, setter, value);
        } catch (NoSuchMethodException e) {
            throw new OptionValidationException(String.format("No setter for Option annotated field '%s' in class '%s'.",
                    getElementName(), getDeclaredClass()));
        }
    }
}

