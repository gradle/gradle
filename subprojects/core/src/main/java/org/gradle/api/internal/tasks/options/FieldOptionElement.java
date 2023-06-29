/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.api.NonNullApi;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

public class FieldOptionElement {

    public static OptionElement create(Option option, Field field, OptionValueNotationParserFactory optionValueNotationParserFactory) {
        String optionName = calOptionName(option, field);
        Class<?> fieldType = field.getType();

        if (Property.class.isAssignableFrom(fieldType)) {
            PropertySetter setter = mutateUsingGetter(field);
            return AbstractOptionElement.of(optionName, option, setter, optionValueNotationParserFactory);
        }
        if (ListProperty.class.isAssignableFrom(fieldType) || SetProperty.class.isAssignableFrom(fieldType)) {
            PropertySetter setter = mutateUsingGetter(field);
            Class<?> elementType = setter.getRawType();
            return new MultipleValueOptionElement(optionName, option, elementType, setter, optionValueNotationParserFactory);
        }

        PropertySetter setter = mutateUsingSetter(field);
        return AbstractOptionElement.of(optionName, option, setter, optionValueNotationParserFactory);
    }

    private static PropertySetter mutateUsingSetter(Field field) {
        return new FieldSetter(getSetter(field), field);
    }

    private static PropertySetter mutateUsingGetter(final Field field) {
        if (ListProperty.class.isAssignableFrom(field.getType())) {
            return new ListPropertyFieldSetter(getGetter(field), field);
        }
        if (SetProperty.class.isAssignableFrom(field.getType())) {
            return new SetPropertyFieldSetter(getGetter(field), field);
        }
        return new PropertyFieldSetter(getGetter(field), field);
    }

    private static String calOptionName(Option option, Field field) {
        if (option.option().length() == 0) {
            return field.getName();
        } else {
            return option.option();
        }
    }

    private static Method getSetter(Field field) {
        try {
            String setterName = "set" + StringUtils.capitalize(field.getName());
            return field.getDeclaringClass().getMethod(setterName, field.getType());
        } catch (NoSuchMethodException e) {
            throw new OptionValidationException(String.format("No setter for Option annotated field '%s' in class '%s'.",
                    field.getName(), field.getDeclaringClass()));
        }
    }

    private static Method getGetter(Field field) {
        try {
            String getterName = "get" + StringUtils.capitalize(field.getName());
            return field.getDeclaringClass().getMethod(getterName);
        } catch (NoSuchMethodException e) {
            throw new OptionValidationException(String.format("No getter for Option annotated field '%s' in class '%s'.",
                    field.getName(), field.getDeclaringClass()));
        }
    }

    private static class FieldSetter implements PropertySetter {
        private final Method setter;
        private final Field field;

        public FieldSetter(Method setter, Field field) {
            this.setter = setter;
            this.field = field;
        }

        @Override
        public Class<?> getDeclaringClass() {
            return field.getDeclaringClass();
        }

        @Override
        public Class<?> getRawType() {
            return setter.getParameterTypes()[0];
        }

        @Override
        public Type getGenericType() {
            return setter.getGenericParameterTypes()[0];
        }

        @Override
        public void setValue(Object target, Object value) {
            JavaMethod.of(Object.class, setter).invoke(target, value);
        }
    }

    private static class PropertyFieldSetter implements PropertySetter {
        private final Method getter;
        private final Field field;
        private final Class<?> elementType;

        public PropertyFieldSetter(Method getter, Field field) {
            this.getter = getter;
            this.field = field;
            this.elementType = ModelType.of(getter.getGenericReturnType()).getTypeVariables().get(0).getRawClass();
        }

        @Override
        public Class<?> getDeclaringClass() {
            return field.getDeclaringClass();
        }

        @Override
        public Class<?> getRawType() {
            return elementType;
        }

        @Override
        public Type getGenericType() {
            return elementType;
        }

        @Override
        public void setValue(Object target, Object value) {
            Property<Object> property = Cast.uncheckedNonnullCast(JavaMethod.of(Object.class, getter).invoke(target));
            property.set(value);
        }

        protected Method getGetter() {
            return getter;
        }
    }

    @NonNullApi
    private static class ListPropertyFieldSetter extends PropertyFieldSetter {
        public ListPropertyFieldSetter(Method getter, Field field) {
            super(getter, field);
        }

        @Override
        public void setValue(Object target, Object value) {
            ListProperty<Object> property = Cast.uncheckedNonnullCast(JavaMethod.of(Object.class, getGetter()).invoke(target));
            property.set((Iterable<?>) value);
        }
    }

    @NonNullApi
    private static class SetPropertyFieldSetter extends PropertyFieldSetter {
        public SetPropertyFieldSetter(Method getter, Field field) {
            super(getter, field);
        }

        @Override
        public void setValue(Object target, Object value) {
            SetProperty<Object> property = Cast.uncheckedNonnullCast(JavaMethod.of(Object.class, getGetter()).invoke(target));
            property.set((Iterable<?>) value);
        }
    }
}

