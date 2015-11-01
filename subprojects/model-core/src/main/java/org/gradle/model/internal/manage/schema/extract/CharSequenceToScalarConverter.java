/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.collect.ImmutableMap;
import org.gradle.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Formatter;
import java.util.Map;

// just a temporary placeholder until the real implementation using NotationConverter/NotationParser is working
public class CharSequenceToScalarConverter {

    private static final String CANDIDATE = "CharSequence instances.";
    private static final Map<Class<?>, Class<?>> UNBOXED_TYPES = ImmutableMap.<Class<?>, Class<?>>builder()
        .put(Byte.class, byte.class)
        .put(Short.class, short.class)
        .put(Integer.class, int.class)
        .put(Float.class, float.class)
        .put(Character.class, char.class)
        .put(Double.class, double.class)
        .put(Long.class, long.class)
        .build();

    public static class UnsupportedNotationException extends RuntimeException {
        public UnsupportedNotationException(String failure) {
            super(new Formatter().format("%s%n", failure)
                                 .format("The following types/formats are supported:")
                                 .format("%n  - %s", CANDIDATE)
                                 .toString());
        }
    }

    public static class TypeConversionException extends RuntimeException {
        public TypeConversionException(String message) {
            super(message);
        }
    }

    @SuppressWarnings("unchecked")
    public static Object convert(Object notation, @SuppressWarnings("rawtypes") Class type, boolean primitive) throws UnsupportedNotationException, TypeConversionException {

        if (notation == null) {

            if (primitive) {
                throw new UnsupportedNotationException("Cannot convert null to a primitive type.");
            }

            return null;
        }

        if (notation instanceof CharSequence) {

            String s = notation.toString();

            if (String.class.equals(type)) {
                return s;
            }

            s = s.trim();

            if (type.isEnum()) {
                try {
                    return Enum.valueOf(type, s);
                } catch (IllegalArgumentException e) {
                    throw new TypeConversionException(
                        String.format("Cannot coerce string value '%s' to an enum value of type '%s' (valid case insensitive values: %s)",
                            s, type.getName(), CollectionUtils.toStringList(Arrays.asList(type.getEnumConstants()))));
                }
            }

            try {
                if (Double.class.equals(type)) {
                    return Double.valueOf(s);
                }
                if (Float.class.equals(type)) {
                    return Float.valueOf(s);
                }
                if (Integer.class.equals(type)) {
                    return Integer.valueOf(s);
                }
                if (Long.class.equals(type)) {
                    return Long.valueOf(s);
                }
                if (Short.class.equals(type)) {
                    return Short.valueOf(s);
                }
                if (Byte.class.equals(type)) {
                    return Byte.valueOf(s);
                }
                if (BigDecimal.class.equals(type)) {
                    return new BigDecimal(s);
                }
                if (BigInteger.class.equals(type)) {
                    return new BigInteger(s);
                }
            } catch (NumberFormatException e) {
                throw new TypeConversionException(String.format("Cannot coerce string value '%s' to type %s",
                    s, getRequestedTypeName(type, primitive)));
            }

            if (Character.class.equals(type)) {

                if (s.length() != 1) {
                    throw new TypeConversionException(String.format("Cannot coerce string value '%s' with length %d to type %s",
                        s, s.length(), getRequestedTypeName(type, primitive)));
                }

                return s.charAt(0);
            }

            if (Boolean.class.equals(type)) {
                return "true".equals(s);
            }
        }

        throw new UnsupportedNotationException("Unsupported type");
    }

    private static String getRequestedTypeName(Class<?> type, boolean primitive) {
        return (primitive ? UNBOXED_TYPES.get(type) : type).getSimpleName();
    }
}
