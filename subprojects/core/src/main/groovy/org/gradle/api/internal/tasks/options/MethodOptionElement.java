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

import org.gradle.internal.typeconversion.NotationParser;

import java.lang.reflect.Method;
import java.util.List;

public class MethodOptionElement extends AbstractOptionElement {

    private final Method method;

    MethodOptionElement(Option option, Method method, Class<?> optionType, NotationParser<CharSequence, ?> notationParser) {
        super(option.option(), option, optionType, method.getDeclaringClass(), notationParser);
        this.method = method;
        assertMethodTypeSupported(getOptionName(), method);
        assertValidOptionName();
    }

    private void assertValidOptionName() {
        if (getOptionName()== null || getOptionName().length() == 0) {
            throw new OptionValidationException(String.format("No option name set on '%s' in class '%s'.", getElementName(), getDeclaredClass().getName()));
        }
    }

    public Class<?> getDeclaredClass() {
        return method.getDeclaringClass();
    }

    public String getElementName() {
        return method.getName();
    }

    public void apply(Object object, List<String> parameterValues) {
        if (parameterValues.size() == 0) {
            invokeMethod(object, method, true);
        } else if (parameterValues.size() > 1  || List.class.equals(getOptionType())) {
            invokeMethod(object, method, parameterValues);
        } else {
            invokeMethod(object, method, getNotationParser().parseNotation(parameterValues.get(0)));
        }
    }

    public static MethodOptionElement create(Option option, Method method, OptionValueNotationParserFactory optionValueNotationParserFactory){
        Class<?> optionType = calculateOptionType(method);
        NotationParser<CharSequence, ?> notationParser = createNotationParserOrFail(optionValueNotationParserFactory, option.option(), optionType, method.getDeclaringClass());
        return new MethodOptionElement(option, method, optionType, notationParser);
    }


    private static Class<?> calculateOptionType(Method optionMethod) {
        if (optionMethod.getParameterTypes().length == 0) {
            return Void.TYPE;
        } else {
            return calculateOptionType(optionMethod.getParameterTypes()[0]);
        }
    }

    private static void assertMethodTypeSupported(String optionName, Method method) {
        final Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length > 1) {
            throw new OptionValidationException(String.format("Option '%s' cannot be linked to methods with multiple parameters in class '%s#%s'.",
                    optionName, method.getDeclaringClass().getName(), method.getName()));
        }
    }
}
