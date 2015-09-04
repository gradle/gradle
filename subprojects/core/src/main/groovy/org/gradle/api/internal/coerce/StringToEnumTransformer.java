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

package org.gradle.api.internal.coerce;

import groovy.lang.MetaProperty;
import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.typeconversion.EnumFromCharSequenceNotationParser;

import java.lang.reflect.Method;

public class StringToEnumTransformer implements MethodArgumentsTransformer, PropertySetTransformer {

    public static final StringToEnumTransformer INSTANCE = new StringToEnumTransformer();

    public Object[] transform(Object target, final String methodName, Object... args) {
        if (args.length != 1 || !(args[0] instanceof CharSequence)) {
            return args;
        }

        final CharSequence charSequenceArg = (CharSequence) args[0];

        Method enumMethod = JavaReflectionUtil.findMethod(target.getClass(), new Spec<Method>() {
            @Override
            public boolean isSatisfiedBy(Method method) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (method.getName().equals(methodName) && parameterTypes.length == 1) {
                    Class<?> parameterType = parameterTypes[0];

                    if (parameterType.isEnum()) {
                        return true; // stop searching
                    }
                }

                return false;
            }
        });

        if (enumMethod == null) {
            return args;
        } else {
            @SuppressWarnings("unchecked")
            Class<? extends Enum> enumType = (Class<? extends Enum>) enumMethod.getParameterTypes()[0];
            return new Object[]{toEnumValue(enumType, charSequenceArg)};
        }
    }

    @Override
    public Object transformValue(Object target, MetaProperty property, Object value) {
        if (value instanceof CharSequence && property.getType().isEnum()) {
            @SuppressWarnings("unchecked") Class<? extends Enum> enumType = (Class<? extends Enum>) property.getType();
            final String setterName = MetaProperty.getSetterName(property.getName());
            Method setter = JavaReflectionUtil.findMethod(target.getClass(), new Spec<Method>() {
                @Override
                public boolean isSatisfiedBy(Method element) {
                    return element.getName().equals(setterName) && element.getParameterTypes().length == 1;
                }
            });

            if (setter == null || setter.getParameterTypes()[0].equals(enumType)) {
                return toEnumValue(enumType, (CharSequence) value);
            }
        }

        return value;
    }

    static public <T extends Enum<T>> T toEnumValue(Class<T> enumType, CharSequence charSequence) {
        EnumFromCharSequenceNotationParser<T> notationParser = new EnumFromCharSequenceNotationParser<T>(enumType);
        return notationParser.parseNotation(charSequence);
    }
}
