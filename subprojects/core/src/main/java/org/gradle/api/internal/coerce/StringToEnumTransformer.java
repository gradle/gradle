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

import org.codehaus.groovy.reflection.CachedClass;
import org.gradle.internal.typeconversion.EnumFromCharSequenceNotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;

public class StringToEnumTransformer implements MethodArgumentsTransformer, PropertySetTransformer {

    public static final StringToEnumTransformer INSTANCE = new StringToEnumTransformer();

    @Override
    public Object[] transform(CachedClass[] types, Object[] args) {
        boolean needsTransform = false;
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            Class type = types[i].getTheClass();
            if (type.isInstance(arg) || arg == null) {
                // Can use arg without conversion
                continue;
            }
            if (!(arg instanceof CharSequence && type.isEnum())) {
                // Cannot convert
                return args;
            }
            needsTransform = true;
        }
        if (!needsTransform) {
            return args;
        }
        Object[] transformed = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            Class type = types[i].getTheClass();
            if (type.isEnum() && arg instanceof CharSequence) {
                transformed[i] = toEnumValue(type, (CharSequence) arg);
            } else {
                transformed[i] = args[i];
            }
        }
        return transformed;
    }

    @Override
    public boolean canTransform(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof CharSequence) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object transformValue(Class<?> type, Object value) {
        if (value instanceof CharSequence && type.isEnum()) {
            @SuppressWarnings("unchecked") Class<? extends Enum> enumType = (Class<? extends Enum>) type;
            return toEnumValue(enumType, (CharSequence) value);
        }

        return value;
    }

    static public <T extends Enum<T>> T toEnumValue(Class<T> enumType, CharSequence charSequence) {
        return NotationParserBuilder
                .toType(enumType)
                .noImplicitConverters()
                .fromCharSequence(new EnumFromCharSequenceNotationParser<T>(enumType))
                .toComposite()
                .parseNotation(charSequence);
    }
}
