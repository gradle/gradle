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

import org.gradle.api.GradleException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class FieldOptionElement implements OptionElement {

    private final Field field;
    private List<String> availableValues;
    private Class<?> argumentType;

    public FieldOptionElement(Field field) {
        // TODO assert field type supported
        this.field = field;
    }

    public Class<?> getOptionType() {
        if (argumentType == null) {
            calculdateAvailableValuesAndTypes();
        }
        return argumentType;
    }

    public String getName() {
        return field.getName();
    }

    public Class<?> getDeclaredClass() {
        return field.getDeclaringClass();
    }

    public List<String> getAvailableValues() {
        //calculate list lazy to avoid overhead upfront
        if (availableValues == null) {
            calculdateAvailableValuesAndTypes();
        }
        return availableValues;
    }


    public void apply(Object object, List<String> parameterValues){
        if(argumentType==Void.TYPE && parameterValues.size() == 0) {
            setFieldValue(object, true);
        } else if (parameterValues.size() > 1) {
            throw new IllegalArgumentException(String.format("Lists not supported for option"));
        } else {
            Object arg = getParameterObject(parameterValues.get(0));
            setFieldValue(object, arg);
        }
    }

    private void setFieldValue(Object object, Object value) {
        field.setAccessible(true);
        try {
            field.set(object, value);
        } catch (IllegalAccessException e) {
            throw new GradleException(String.format("Cannot apply option value %s on field %s of object %s", value, field.getName(), object));
        }
    }

    private Object getParameterObject(String value) {
        if (getOptionType().isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) getOptionType(), value);
        }
        return value;
    }

    private void calculdateAvailableValuesAndTypes() {
        availableValues = new ArrayList<String>();

        Class<?> type = field.getType();
        //we don't want to support "--flag true" syntax
        if (type == Boolean.class || type == Boolean.TYPE) {
            argumentType = Void.TYPE;
        } else {
            argumentType = type;
            if (argumentType.isEnum()) {
                final Enum[] enumConstants = (Enum[]) argumentType.getEnumConstants();
                for (Enum enumConstant : enumConstants) {
                    availableValues.add(enumConstant.name());
                }
            }
        }
    }
}

