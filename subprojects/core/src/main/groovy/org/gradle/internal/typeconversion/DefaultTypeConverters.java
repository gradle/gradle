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

package org.gradle.internal.typeconversion;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.exceptions.DiagnosticsVisitor;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class DefaultTypeConverters implements TypeConverters {

    private static final String CANDIDATE = "CharSequence instances.";
    private static final Collection<String> CANDIDATES = Collections.singleton(CANDIDATE);
    private static final Map<Class<?>, NotationParser<Object, ?>> PARSERS = Maps.newHashMap();
    private static final Map<Class<?>, Class<?>> UNBOXED_TYPES = ImmutableMap.<Class<?>, Class<?>>builder()
        .put(Byte.class, byte.class)
        .put(Short.class, short.class)
        .put(Integer.class, int.class)
        .put(Boolean.class, boolean.class)
        .put(Float.class, float.class)
        .put(Character.class, char.class)
        .put(Double.class, double.class)
        .put(Long.class, long.class)
        .build();
    private final NotationParser<Object, File> fileParser;

    @SuppressWarnings("unchecked")
    private static <T> NotationParser<Object, T> build(CharSequenceConverter converter, Class<?> type) {
        return NotationParserBuilder
            .toType(type)
            .noImplicitConverters()
            .converter(converter)
            .toComposite();
    }

    private static void registerConverter(CharSequenceConverter converter, Class<?>... types) {
        for (Class<?> type : types) {
            PARSERS.put(type, build(converter, type));
        }
    }

    private static void convertToCharacter(Object notation, NotationConvertResult<? super Character> result, Class<?> type) throws TypeConversionException {

        String trimmed = notation.toString().trim();
        if (trimmed.length() != 1) {
            throw new TypeConversionException(String.format("Cannot coerce string value '%s' with length %d to type %s",
                trimmed, trimmed.length(), type.getSimpleName()));
        }

        result.converted(trimmed.charAt(0));
    }

    static {

        registerConverter(new NumberConverter<Double>(Double.class) {
            protected void convertStringToNumber(String s, NotationConvertResult<? super Double> result) throws TypeConversionException {
                result.converted(Double.valueOf(s.toString()));
            }
            protected void convertNumberToNumber(Number n, NotationConvertResult<? super Double> result) {
                result.converted(n.doubleValue());
            }
        }, Double.class);
        registerConverter(new NumberConverter<Double>(double.class) {
            protected void convertStringToNumber(String s, NotationConvertResult<? super Double> result) throws TypeConversionException {
                result.converted(Double.valueOf(s.toString()));
            }
            protected void convertNumberToNumber(Number n, NotationConvertResult<? super Double> result) {
                result.converted(n.doubleValue());
            }
        }, double.class);

        registerConverter(new NumberConverter<Float>(Float.class) {
            protected void convertStringToNumber(String s, NotationConvertResult<? super Float> result) throws TypeConversionException {
                result.converted(Float.valueOf(s.toString()));
            }
            protected void convertNumberToNumber(Number n, NotationConvertResult<? super Float> result) {
                result.converted(n.floatValue());
            }
        }, Float.class);
        registerConverter(new NumberConverter<Float>(float.class) {
            protected void convertStringToNumber(String s, NotationConvertResult<? super Float> result) throws TypeConversionException {
                result.converted(Float.valueOf(s.toString()));
            }
            protected void convertNumberToNumber(Number n, NotationConvertResult<? super Float> result) {
                result.converted(n.floatValue());
            }
        }, float.class);

        registerConverter(new NumberConverter<Integer>(Integer.class) {
            protected void convertStringToNumber(String s, NotationConvertResult<? super Integer> result) throws TypeConversionException {
                result.converted(Integer.valueOf(s.toString()));
            }
            protected void convertNumberToNumber(Number n, NotationConvertResult<? super Integer> result) {
                result.converted(n.intValue());
            }
        }, Integer.class);
        registerConverter(new NumberConverter<Integer>(int.class) {
            protected void convertStringToNumber(String s, NotationConvertResult<? super Integer> result) throws TypeConversionException {
                result.converted(Integer.valueOf(s.toString()));
            }
            protected void convertNumberToNumber(Number n, NotationConvertResult<? super Integer> result) {
                result.converted(n.intValue());
            }
        }, int.class);

        registerConverter(new NumberConverter<Long>(Long.class) {
            protected void convertStringToNumber(String s, NotationConvertResult<? super Long> result) throws TypeConversionException {
                result.converted(Long.valueOf(s.toString()));
            }
            protected void convertNumberToNumber(Number n, NotationConvertResult<? super Long> result) {
                result.converted(n.longValue());
            }
        }, Long.class);
        registerConverter(new NumberConverter<Long>(long.class) {
            protected void convertStringToNumber(String s, NotationConvertResult<? super Long> result) throws TypeConversionException {
                result.converted(Long.valueOf(s.toString()));
            }
            protected void convertNumberToNumber(Number n, NotationConvertResult<? super Long> result) {
                result.converted(n.longValue());
            }
        }, long.class);

        registerConverter(new NumberConverter<Short>(Short.class) {
            protected void convertStringToNumber(String s, NotationConvertResult<? super Short> result) throws TypeConversionException {
                result.converted(Short.valueOf(s.toString()));
            }
            protected void convertNumberToNumber(Number n, NotationConvertResult<? super Short> result) {
                result.converted(n.shortValue());
            }
        }, Short.class);
        registerConverter(new NumberConverter<Short>(short.class) {
            protected void convertStringToNumber(String s, NotationConvertResult<? super Short> result) throws TypeConversionException {
                result.converted(Short.valueOf(s.toString()));
            }
            protected void convertNumberToNumber(Number n, NotationConvertResult<? super Short> result) {
                result.converted(n.shortValue());
            }
        }, short.class);

        registerConverter(new NumberConverter<Byte>(Byte.class) {
            protected void convertStringToNumber(String s, NotationConvertResult<? super Byte> result) throws TypeConversionException {
                result.converted(Byte.valueOf(s.toString()));
            }
            protected void convertNumberToNumber(Number n, NotationConvertResult<? super Byte> result) {
                result.converted(n.byteValue());
            }
        }, Byte.class);
        registerConverter(new NumberConverter<Byte>(byte.class) {
            protected void convertStringToNumber(String s, NotationConvertResult<? super Byte> result) throws TypeConversionException {
                result.converted(Byte.valueOf(s.toString()));
            }
            protected void convertNumberToNumber(Number n, NotationConvertResult<? super Byte> result) {
                result.converted(n.byteValue());
            }
        }, byte.class);

        registerConverter(new NumberConverter<BigDecimal>(BigDecimal.class) {
            protected void convertStringToNumber(String s, NotationConvertResult<? super BigDecimal> result) throws TypeConversionException {
                result.converted(new BigDecimal(s.toString()));
            }
            protected void convertNumberToNumber(Number n, NotationConvertResult<? super BigDecimal> result) {
                if (n instanceof BigDecimal) {
                    result.converted((BigDecimal)n);
                } else if (n instanceof BigInteger) {
                    result.converted(new BigDecimal((BigInteger)n));
                }
            }
        }, BigDecimal.class);

        registerConverter(new NumberConverter<BigInteger>(BigInteger.class) {
            protected void convertStringToNumber(String s, NotationConvertResult<? super BigInteger> result) throws TypeConversionException {
                result.converted(new BigInteger(s.toString()));
            }
            protected void convertNumberToNumber(Number n, NotationConvertResult<? super BigInteger> result) {
                if (n instanceof BigInteger) {
                    result.converted((BigInteger)n);
                }
            }
        }, BigInteger.class);

        CharSequenceConverter<Boolean> booleanConverter = new CharSequenceConverter<Boolean>() {
            public void convert(Object notation, NotationConvertResult<? super Boolean> result) throws TypeConversionException {
                result.converted("true".equals(notation.toString().trim()));
            }
        };
        registerConverter(booleanConverter, Boolean.class, boolean.class);

        registerConverter(new CharSequenceConverter<Character>() {
            public void convert(Object notation, NotationConvertResult<? super Character> result) throws TypeConversionException {
                convertToCharacter(notation, result, Character.class);
            }
        }, Character.class);
        registerConverter(new CharSequenceConverter<Character>() {
            public void convert(Object notation, NotationConvertResult<? super Character> result) throws TypeConversionException {
                convertToCharacter(notation, result, char.class);
            }
        }, char.class);

        registerConverter(new CharSequenceConverter<String>() {
            public void convert(Object notation, NotationConvertResult<? super String> result) throws TypeConversionException {
                result.converted(notation.toString());
            }
        }, String.class);
    }

    public abstract static class CharSequenceConverter<T> implements NotationConverter<Object, T> {
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate(CANDIDATE);
        }
    };

    public abstract static class NumberConverter<T extends Number> extends CharSequenceConverter<T> {
        private final Class<T> type;

        protected NumberConverter(Class<T> type) {
            this.type = type;
        }

        public void convert(Object notation, NotationConvertResult<? super T> result) throws TypeConversionException {
            if (notation instanceof CharSequence) {
                try {
                    convertStringToNumber(notation.toString().trim(), result);
                } catch (NumberFormatException e) {
                    throw new TypeConversionException(String.format("Cannot coerce string value '%s' to type %s",
                        notation, type.getSimpleName()));
                }
            } else if (notation instanceof Number) {
                try {
                    convertNumberToNumber((Number)notation, result);
                } catch (NumberFormatException e) {
                    throw new TypeConversionException(String.format("Cannot coerce numeric value '%s' to type %s",
                        notation, type.getSimpleName()));
                }
            }
        }

        protected abstract void convertStringToNumber(String s, NotationConvertResult<? super T> result);
        protected abstract void convertNumberToNumber(Number n, NotationConvertResult<? super T> result);
    };

    public static class EnumConverter<T extends Enum> extends CharSequenceConverter<T> {
        private final Class<? extends T> enumType;

        public EnumConverter(Class<? extends T> enumType) {
            this.enumType = enumType;
        }

        public void convert(Object notation, NotationConvertResult<? super T> result) throws TypeConversionException {
            if (notation instanceof CharSequence) {
                result.converted((T)new EnumFromCharSequenceNotationParser(enumType).parseNotation(notation.toString().trim()));
            }
        }
    }

    public DefaultTypeConverters(final FileResolver fileResolver) {
        fileParser = build(new CharSequenceConverter<File>() {
            public void convert(Object notation, NotationConvertResult<? super File> result) throws TypeConversionException {
                if (notation instanceof CharSequence) {
                    result.converted(fileResolver.resolve(notation.toString().trim()));
                }
            }
        }, File.class);
    }

    public Object convert(Object notation, Class type, boolean primitive) throws UnsupportedNotationException, TypeConversionException {

        if (notation == null) {

            if (primitive) {
                throw new UnsupportedNotationException(notation,
                    String.format("Cannot assign null value to primitive type %s.", UNBOXED_TYPES.get(type).getSimpleName()), null, CANDIDATES);
            }

            return null;
        }

        if (type.isEnum()) {
            return NotationParserBuilder
                .toType(type)
                .noImplicitConverters()
                .converter(new EnumConverter(type))
                .toComposite().parseNotation(notation);
        }

        NotationParser<Object, ?> parser;
        if (File.class.equals(type)) {
            parser = fileParser;
        } else {
            parser = PARSERS.get(primitive ? UNBOXED_TYPES.get(type) : type);
            if (parser == null) {
                throw new UnsupportedNotationException(notation, "Unsupported type", null, CANDIDATES);
            }
        }

        return parser.parseNotation(notation);
    }
}
