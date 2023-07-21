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

import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileSystemLocationProperty;
import org.gradle.api.provider.HasMultipleValues;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.model.internal.type.ModelType;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

public class MethodOptionElement {

    private static String assertValidOptionName(Option option, String elementName, Class<?> declaredClass) {
        if (option.option().length() == 0) {
            throw new OptionValidationException(String.format("No option name set on '%s' in class '%s'.", elementName, declaredClass.getName()));
        }
        return option.option();
    }

    public static OptionElement create(Option option, Method method, OptionValueNotationParserFactory optionValueNotationParserFactory) {
        String optionName = assertValidOptionName(option, method.getName(), method.getDeclaringClass());
        if (Property.class.isAssignableFrom(method.getReturnType())) {
            assertCanUseMethodReturnType(optionName, method);
            PropertySetter setter = mutateUsingReturnValue(method);
            return AbstractOptionElement.of(optionName, option, setter, optionValueNotationParserFactory);
        }
        if (HasMultipleValues.class.isAssignableFrom(method.getReturnType())) {
            assertCanUseMethodReturnType(optionName, method);
            PropertySetter setter = mutateUsingReturnValue(method);
            Class<?> elementType = setter.getRawType();
            return new MultipleValueOptionElement(optionName, option, elementType, setter, optionValueNotationParserFactory);
        }
        if (method.getParameterTypes().length == 0) {
            return new BooleanOptionElement(optionName, option, setFlagUsingMethod(method));
        }

        assertCanUseMethodParam(optionName, method);
        PropertySetter setter = mutateUsingParameter(method);
        return AbstractOptionElement.of(optionName, option, setter, optionValueNotationParserFactory);
    }

    private static PropertySetter setFlagUsingMethod(final Method method) {
        return new MethodInvokingSetter(method);
    }

    private static PropertySetter mutateUsingParameter(Method method) {
        return new MethodPropertySetter(method);
    }

    private static PropertySetter mutateUsingReturnValue(Method method) {
        if (HasMultipleValues.class.isAssignableFrom(method.getReturnType())) {
            return new MultipleValuePropertyValueSetter(method);
        }
        if (FileSystemLocationProperty.class.isAssignableFrom(method.getReturnType())) {
            return new FileSystemLocationPropertyValueSetter(method);
        }
        return new PropertyValueSetter(method);
    }

    private static void assertCanUseMethodReturnType(String optionName, Method method) {
        final Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 0) {
            throw new OptionValidationException(String.format("Option '%s' on method that returns %s cannot take parameters in class '%s#%s'.",
                    optionName, method.getGenericReturnType(), method.getDeclaringClass().getName(), method.getName()));
        }
    }

    private static void assertCanUseMethodParam(String optionName, Method method) {
        final Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length > 1) {
            throw new OptionValidationException(String.format("Option '%s' on method cannot take multiple parameters in class '%s#%s'.",
                    optionName, method.getDeclaringClass().getName(), method.getName()));
        }
    }

    private static class MethodPropertySetter implements PropertySetter {
        private final Method method;

        public MethodPropertySetter(Method method) {
            this.method = method;
        }

        @Override
        public Class<?> getDeclaringClass() {
            return method.getDeclaringClass();
        }

        @Override
        public Class<?> getRawType() {
            return method.getParameterTypes()[0];
        }

        @Override
        public Type getGenericType() {
            return method.getGenericParameterTypes()[0];
        }

        @Override
        public void setValue(Object target, Object value) {
            JavaMethod.of(Object.class, method).invoke(target, value);
        }
    }

    private static class PropertyValueSetter implements PropertySetter {
        private final Method method;
        private final Class<?> elementType;

        public PropertyValueSetter(Method method) {
            this.method = method;
            this.elementType = ModelType.of(method.getGenericReturnType()).getTypeVariables().get(0).getRawClass();
        }
        public PropertyValueSetter(Method method, Class<?> elementType) {
            this.method = method;
            this.elementType = elementType;
        }

        @Override
        public Class<?> getDeclaringClass() {
            return method.getDeclaringClass();
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
            Property<Object> property = Cast.uncheckedNonnullCast(JavaMethod.of(Object.class, method).invoke(target));
            property.set(value);
        }

        protected Method getMethod() {
            return method;
        }
    }

    @NonNullApi
    private static class MultipleValuePropertyValueSetter extends PropertyValueSetter {
        public MultipleValuePropertyValueSetter(Method method) {
            super(method);
        }

        @Override
        public void setValue(Object target, Object value) {
            HasMultipleValues<Object> property = Cast.uncheckedNonnullCast(JavaMethod.of(Object.class, getMethod()).invoke(target));
            property.set((Iterable<?>) value);
        }
    }

    @NonNullApi
    private static class FileSystemLocationPropertyValueSetter extends PropertyValueSetter {
        public FileSystemLocationPropertyValueSetter(Method method) {
            super(method, FileSystemLocation.class);
        }

        @Override
        public void setValue(Object target, Object value) {
            FileSystemLocationProperty<FileSystemLocation> property = Cast.uncheckedNonnullCast(JavaMethod.of(Object.class, getMethod()).invoke(target));
            property.set(new File((String)value));
        }
    }

    private static class MethodInvokingSetter implements PropertySetter {
        private final Method method;

        public MethodInvokingSetter(Method method) {
            this.method = method;
        }

        @Override
        public Class<?> getDeclaringClass() {
            return method.getDeclaringClass();
        }

        @Override
        public Class<?> getRawType() {
            return Void.TYPE;
        }

        @Override
        public Type getGenericType() {
            return Void.TYPE;
        }

        @Override
        public void setValue(Object object, Object value) {
            JavaMethod.of(Object.class, method).invoke(object);
        }
    }
}
