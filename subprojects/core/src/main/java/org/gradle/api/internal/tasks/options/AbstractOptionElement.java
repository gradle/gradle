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

import org.gradle.api.tasks.options.Option;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.model.internal.type.ModelType;

import java.lang.annotation.IncompleteAnnotationException;
import java.lang.reflect.Method;
import java.util.List;

abstract class AbstractOptionElement implements OptionElement {
    private final String optionName;
    private final String description;
    private final Class<?> optionType;

    public AbstractOptionElement(String optionName, Option option, Class<?> optionType, Class<?> declaringClass) {
        this(readDescription(option, optionName, declaringClass), optionName, optionType);
    }

    protected AbstractOptionElement(String description, String optionName, Class<?> optionType) {
        if (description == null) {
            throw new OptionValidationException(String.format("No description set on option '%s'.", optionName));
        }
        this.description = description;
        this.optionName = optionName;
        this.optionType = optionType;
    }

    @Override
    public Class<?> getOptionType() {
        return optionType;
    }

    public static OptionElement of(String optionName, Option option, PropertySetter setter, OptionValueNotationParserFactory notationParserFactory) {
        if (setter.getRawType().equals(Boolean.class) || setter.getRawType().equals(Boolean.TYPE)) {
            return new BooleanOptionElement(optionName, option, setter);
        }
        if (setter.getRawType().equals(List.class)) {
            Class<?> elementType = ModelType.of(setter.getGenericType()).getTypeVariables().get(0).getRawClass();
            return new MultipleValueOptionElement(optionName, option, elementType, setter, notationParserFactory);
        }
        return new SingleValueOptionElement(optionName, option, setter.getRawType(), setter, notationParserFactory);
    }

    private static String readDescription(Option option, String optionName, Class<?> declaringClass) {
        try {
            return option.description();
        } catch (IncompleteAnnotationException ex) {
            throw new OptionValidationException(String.format("No description set on option '%s' at for class '%s'.", optionName, declaringClass.getName()));
        }
    }

    protected Object invokeMethod(Object object, Method method, Object... parameterValues) {
        final JavaMethod<Object, Object> javaMethod = JavaMethod.of(Object.class, method);
        return javaMethod.invoke(object, parameterValues);
    }

    @Override
    public String getOptionName() {
        return optionName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    protected static <T> NotationParser<CharSequence, T> createNotationParserOrFail(OptionValueNotationParserFactory optionValueNotationParserFactory, String optionName, Class<T> optionType, Class<?> declaringClass) {
        try {
            return optionValueNotationParserFactory.toComposite(optionType);
        } catch (OptionValidationException ex) {
            throw new OptionValidationException(String.format("Option '%s' cannot be cast to type '%s' in class '%s'.",
                    optionName, optionType.getName(), declaringClass.getName()));
        }
    }

    protected static Class<?> calculateOptionType(Class<?> type) {
        //we don't want to support "--flag true" syntax
        if (type == Boolean.class || type == Boolean.TYPE) {
            return Void.TYPE;
        } else {
            return type;
        }
    }
}
