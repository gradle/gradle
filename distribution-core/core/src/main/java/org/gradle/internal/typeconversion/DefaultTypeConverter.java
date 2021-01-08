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
import org.gradle.internal.Cast;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.file.PathToFileResolver;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

public class DefaultTypeConverter implements TypeConverter {
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
    private final Map<Class<?>, NotationParser<Object, ?>> parsers = Maps.newHashMap();

    private static <T> NotationParser<Object, T> build(NotationConverter<Object, T> converter, Class<T> type) {
        return NotationParserBuilder
            .toType(type)
            .noImplicitConverters()
            .converter(converter)
            .toComposite();
    }

    private <T> void registerConverter(NotationConverter<Object, T> converter, Class<T> type) {
        parsers.put(type, build(converter, type));
    }

    private <T> void registerStringConverter(NotationConverter<String, T> converter, Class<T> type) {
        parsers.put(type, build(new CharSequenceNotationConverter<Object, T>(converter), type));
    }

    private void registerConverters() {
        registerConverter(new DoubleNumberConverter(Double.class), Double.class);
        registerConverter(new DoubleNumberConverter(double.class), double.class);
        registerConverter(new FloatNumberConverter(Float.class), Float.class);
        registerConverter(new FloatNumberConverter(float.class), float.class);
        registerConverter(new IntegerNumberConverter(Integer.class), Integer.class);
        registerConverter(new IntegerNumberConverter(int.class), int.class);
        registerConverter(new LongNumberConverter(Long.class), Long.class);
        registerConverter(new LongNumberConverter(long.class), long.class);
        registerConverter(new ShortNumberConverter(Short.class), Short.class);
        registerConverter(new ShortNumberConverter(short.class), short.class);
        registerConverter(new ByteNumberConverter(Byte.class), Byte.class);
        registerConverter(new ByteNumberConverter(byte.class), byte.class);
        registerConverter(new BigDecimalNumberConverter(), BigDecimal.class);
        registerConverter(new BigIntegerNumberConverter(), BigInteger.class);

        CharSequenceConverter<Boolean> booleanConverter = new BooleanConverter();
        registerStringConverter(booleanConverter, Boolean.class);
        registerStringConverter(booleanConverter, boolean.class);

        registerStringConverter(new CharacterConverter(Character.class, Character.class), Character.class);
        registerStringConverter(new CharacterConverter(Character.class, char.class), char.class);

        registerConverter(new StringConverter(), String.class);
    }

    private abstract static class CharSequenceConverter<T> implements NotationConverter<String, T> {
        final Class<T> type;

        public CharSequenceConverter(Class<T> type) {
            this.type = type;
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("A String or CharSequence");
            visitor.candidate("A " + type.getSimpleName());
        }
    }

    private static class StringConverter implements NotationConverter<Object, String> {
        @Override
        public void convert(Object notation, NotationConvertResult<? super String> result) throws TypeConversionException {
            if (notation instanceof CharSequence || notation instanceof Number || notation instanceof Boolean || notation instanceof Character || notation instanceof File) {
                result.converted(notation.toString());
            }
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("A String or CharSequence or Character");
            visitor.candidate("Any Number");
            visitor.candidate("A Boolean");
            visitor.candidate("A File");
        }
    }

    private abstract static class NumberConverter<T extends Number> implements NotationConverter<Object, T> {
        private final Class<T> type;

        protected NumberConverter(Class<T> type) {
            this.type = type;
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("A String or CharSequence");
            visitor.candidate("Any Number");
        }

        @Override
        public void convert(Object notation, NotationConvertResult<? super T> result) throws TypeConversionException {
            if (notation instanceof CharSequence) {
                try {
                    convertNumberToNumber(new BigDecimal(notation.toString().trim()), result);
                } catch (ArithmeticException | NumberFormatException e) {
                    throw new TypeConversionException(String.format("Cannot convert value '%s' to type %s",
                        notation, type.getSimpleName()), e);
                }
            } else if (notation instanceof Number) {
                try {
                    convertNumberToNumber(toBigDecimal((Number) notation), result);
                } catch (ArithmeticException e) {
                    throw new TypeConversionException(String.format("Cannot convert value '%s' to type %s",
                        notation, type.getSimpleName()), e);
                }
            }
        }

        private static BigDecimal toBigDecimal(Number notation) {
            if (notation instanceof BigDecimal) {
                return (BigDecimal) notation;
            }
            if (notation instanceof BigInteger) {
                return new BigDecimal((BigInteger) notation);
            }
            if (notation instanceof Float) {
                return new BigDecimal(notation.floatValue());
            }
            if (notation instanceof Double) {
                return new BigDecimal(notation.doubleValue());
            }
            return new BigDecimal(notation.longValue());
        }

        protected abstract void convertNumberToNumber(BigDecimal n, NotationConvertResult<? super T> result);
    }

    public DefaultTypeConverter(final PathToFileResolver fileResolver) {
        registerConverter(new CharSequenceNotationConverter<Object, File>(new CharSequenceConverter<File>(File.class) {
            @Override
            public void convert(String notation, NotationConvertResult<? super File> result) throws TypeConversionException {
                result.converted(fileResolver.resolve(notation));
            }
        }), File.class);
        registerConverters();
    }

    @Override
    public Object convert(Object notation, Class<?> type, boolean primitive) throws TypeConversionException {
        if (type.isInstance(notation)) {
            return notation;
        }
        if (!primitive && notation == null) {
            return null;
        }

        if (type.isEnum()) {
            return convertEnum(Cast.uncheckedCast(type), notation);
        }

        NotationParser<Object, ?> parser;
        parser = parsers.get(primitive ? UNBOXED_TYPES.get(type) : type);
        if (parser == null) {
            throw new IllegalArgumentException("Don't know how to convert to type " + type.getName());
        }

        return parser.parseNotation(notation);
    }

    private <T extends Enum<T>> T convertEnum(Class<T> type, Object notation) {
        return NotationParserBuilder
                .toType(type)
                .noImplicitConverters()
                .fromCharSequence(new EnumFromCharSequenceNotationParser<T>(type))
                .toComposite()
                .parseNotation(notation);
    }

    private static class DoubleNumberConverter extends NumberConverter<Double> {
        public DoubleNumberConverter(Class<Double> cl) {
            super(cl);
        }

        @Override
        protected void convertNumberToNumber(BigDecimal n, NotationConvertResult<? super Double> result) {
            result.converted(n.doubleValue());
        }
    }

    private static class FloatNumberConverter extends NumberConverter<Float> {
        public FloatNumberConverter(Class<Float> cl) {
            super(cl);
        }

        @Override
        protected void convertNumberToNumber(BigDecimal n, NotationConvertResult<? super Float> result) {
            result.converted(n.floatValue());
        }
    }

    private static class IntegerNumberConverter extends NumberConverter<Integer> {
        public IntegerNumberConverter(Class<Integer> cl) {
            super(cl);
        }

        @Override
        protected void convertNumberToNumber(BigDecimal n, NotationConvertResult<? super Integer> result) {
            result.converted(n.intValueExact());
        }
    }

    private static class LongNumberConverter extends NumberConverter<Long> {
        public LongNumberConverter(Class<Long> cl) {
            super(cl);
        }

        @Override
        protected void convertNumberToNumber(BigDecimal n, NotationConvertResult<? super Long> result) {
            result.converted(n.longValueExact());
        }
    }

    private static class ShortNumberConverter extends NumberConverter<Short> {
        public ShortNumberConverter(Class<Short> cl) {
            super(cl);
        }

        @Override
        protected void convertNumberToNumber(BigDecimal n, NotationConvertResult<? super Short> result) {
            result.converted(n.shortValueExact());
        }
    }

    private static class ByteNumberConverter extends NumberConverter<Byte> {
        public ByteNumberConverter(Class<Byte> cl) {
            super(cl);
        }

        @Override
        protected void convertNumberToNumber(BigDecimal n, NotationConvertResult<? super Byte> result) {
            result.converted(n.byteValueExact());
        }
    }

    private static class BigDecimalNumberConverter extends NumberConverter<BigDecimal> {
        public BigDecimalNumberConverter() {
            super(BigDecimal.class);
        }

        @Override
        protected void convertNumberToNumber(BigDecimal n, NotationConvertResult<? super BigDecimal> result) {
            result.converted(n);
        }
    }

    private static class BigIntegerNumberConverter extends NumberConverter<BigInteger> {
        public BigIntegerNumberConverter() {
            super(BigInteger.class);
        }

        @Override
        protected void convertNumberToNumber(BigDecimal n, NotationConvertResult<? super BigInteger> result) {
            result.converted(n.toBigIntegerExact());
        }
    }

    private static class BooleanConverter extends CharSequenceConverter<Boolean> {
        public BooleanConverter() {
            super(Boolean.class);
        }

        @Override
        public void convert(String notation, NotationConvertResult<? super Boolean> result) throws TypeConversionException {
            result.converted("true".equals(notation));
        }
    }

    private static class CharacterConverter extends CharSequenceConverter<Character> {
        private final Class<Character> target;

        public CharacterConverter(Class<Character> boxed, Class<Character> target) {
            super(boxed);
            this.target = target;
        }

        @Override
        public void convert(String notation, NotationConvertResult<? super Character> result) throws TypeConversionException {
            if (notation.length() != 1) {
                throw new TypeConversionException(String.format("Cannot convert string value '%s' with length %d to type %s",
                        notation, notation.length(), target.getSimpleName()));
            }

            result.converted(notation.charAt(0));
        }
    }
}
