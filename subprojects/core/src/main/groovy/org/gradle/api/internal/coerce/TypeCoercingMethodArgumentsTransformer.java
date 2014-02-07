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

import org.gradle.api.Transformer;
import org.gradle.internal.typeconversion.EnumFromCharSequenceNotationParser;
import org.gradle.internal.reflect.JavaReflectionUtil;

import java.lang.reflect.Method;
import java.util.ArrayList;
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

        final CharSequence charSequenceArg = (CharSequence) args[0];

        final List<Method> enumMethodHolder = new ArrayList<Method>(2);
        final List<Method> stringMethodHolder = new ArrayList<Method>(1);

        JavaReflectionUtil.searchMethods(target.getClass(), new Transformer<Boolean, Method>() {
            public Boolean transform(Method method) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (method.getName().equals(methodName) && parameterTypes.length == 1) {
                    Class<?> parameterType = parameterTypes[0];

                    if (parameterType.isAssignableFrom(charSequenceArg.getClass())) {
                        stringMethodHolder.add(method);
                        return true; // stop searching
                    } else if (parameterType.isEnum()) {
                        enumMethodHolder.add(method);
                        if (enumMethodHolder.size() > 1) {
                            return true; // stop searching
                        }
                    }
                }

                return false;
            }
        });

        // There's a method that takes the uncoerced type
        if (!stringMethodHolder.isEmpty()) {
            return args;
        }

        // There's either no enum method, or more than one
        if (enumMethodHolder.size() != 1) {
            return args;
        }

        // Match, we can try and coerce
        Method match = enumMethodHolder.get(0);
        @SuppressWarnings("unchecked")
        Class<? extends Enum> enumType = (Class<? extends Enum>) match.getParameterTypes()[0];
        return new Object[]{toEnumValue(enumType, charSequenceArg)};
    }

    public <T extends Enum<T>> T toEnumValue(Class<T> enumType, CharSequence charSequence) {
        EnumFromCharSequenceNotationParser<T> notationParser = new EnumFromCharSequenceNotationParser<T>(enumType);
        return notationParser.parseNotation(charSequence);
    }
}
