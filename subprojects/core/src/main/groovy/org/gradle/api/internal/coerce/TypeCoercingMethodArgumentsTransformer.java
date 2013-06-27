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

import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/*
    This guy is hardcoded to just deal with coercing calls to one arg enum methods, from char sequence values.
    It will need to be made pluggable as more coercions come online.

    Also need to consider some caching in here as there's potentially a lot of repetitive reflection.
 */
public class TypeCoercingMethodArgumentsTransformer implements MethodArgumentsTransformer {

    public Object[] transform(Object target, String methodName, Object... args) {
        return maybeTransformForEnum(target, methodName, args);
    }

    private Object[] maybeTransformForEnum(Object target, final String methodName, Object... args) {
        if (args.length != 1 || !(args[0] instanceof CharSequence)) {
            return args;
        }

        CharSequence charSequenceArg = (CharSequence) args[0];

        List<Method> singleArgEnumMethods = JavaReflectionUtil.findAllMethods(target.getClass(), new Spec<Method>() {
            public boolean isSatisfiedBy(Method method) {
                return method.getName().equals(methodName)
                        && method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0].isEnum();
            }
        });

        if (singleArgEnumMethods.size() != 1) {
            return args;
        }

        // Match, we can try and coerce
        Method match = singleArgEnumMethods.get(0);
        @SuppressWarnings("unchecked")
        Class<? extends Enum> enumType = (Class<? extends Enum>) match.getParameterTypes()[0];
        return new Object[]{toEnumValue(enumType, charSequenceArg)};
    }

    private <T extends Enum<T>> T toEnumValue(Class<T> enumType, CharSequence charSequence) {
        final String enumString = charSequence.toString();
        List<T> enumConstants = Arrays.asList(enumType.getEnumConstants());
        T match = CollectionUtils.findFirst(enumConstants, new Spec<T>() {
            public boolean isSatisfiedBy(T enumValue) {
                return enumValue.name().equalsIgnoreCase(enumString);
            }
        });

        if (match == null) {
            throw new TypeCoercionException(
                    String.format("Cannot coerce string value '%s' to an enum value of type '%s' (valid case insensitive values: %s)",
                            enumString, enumType.getName(), CollectionUtils.toStringList(Arrays.asList(enumType.getEnumConstants()))
                    )
            );
        } else {
            return match;
        }
    }

}
