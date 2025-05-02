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

package org.gradle.language.nativeplatform.internal.incremental.sourceparser;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.serialize.AbstractCollectionSerializer;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.language.nativeplatform.internal.Expression;
import org.gradle.language.nativeplatform.internal.Include;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.gradle.language.nativeplatform.internal.IncludeType;
import org.gradle.language.nativeplatform.internal.Macro;
import org.gradle.language.nativeplatform.internal.MacroFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class IncludeDirectivesSerializer implements Serializer<IncludeDirectives> {
    public static final IncludeDirectivesSerializer INSTANCE = new IncludeDirectivesSerializer();

    private final Serializer<IncludeType> enumSerializer = new BaseSerializerFactory().getSerializerFor(IncludeType.class);
    private final Serializer<Expression> expressionSerializer = new ExpressionSerializer(enumSerializer);
    private final ListSerializer<Include> includeListSerializer = new ListSerializer<Include>(new IncludeSerializer(enumSerializer, expressionSerializer));
    private final CollectionSerializer<Macro> macroListSerializer = new CollectionSerializer<Macro>(new MacroSerializer(enumSerializer, expressionSerializer));
    private final CollectionSerializer<MacroFunction> macroFunctionListSerializer = new CollectionSerializer<MacroFunction>(new MacroFunctionSerializer(enumSerializer, expressionSerializer));

    private IncludeDirectivesSerializer() {
    }

    @Override
    public IncludeDirectives read(Decoder decoder) throws Exception {
        return DefaultIncludeDirectives.of(ImmutableList.copyOf(includeListSerializer.read(decoder)), ImmutableList.copyOf(macroListSerializer.read(decoder)), ImmutableList.copyOf(macroFunctionListSerializer.read(decoder)));
    }

    @Override
    public void write(Encoder encoder, IncludeDirectives value) throws Exception {
        includeListSerializer.write(encoder, value.getAll());
        macroListSerializer.write(encoder, value.getAllMacros());
        macroFunctionListSerializer.write(encoder, value.getAllMacroFunctions());
    }

    private static class ExpressionSerializer implements Serializer<Expression> {
        private static final byte SIMPLE = (byte) 1;
        private static final byte COMPLEX_ONE_ARG = (byte) 2;
        private static final byte COMPLEX_MULTIPLE_ARGS = (byte) 3;
        private static final byte EMPTY_TOKENS = (byte) 4;
        private static final byte EMPTY_ARGS = (byte) 5;
        private static final byte COMMA = (byte) 6;
        private static final byte LEFT_PAREN = (byte) 7;
        private static final byte RIGHT_PAREN = (byte) 8;
        private final Serializer<IncludeType> enumSerializer;
        private final Serializer<List<Expression>> argsSerializer;

        ExpressionSerializer(Serializer<IncludeType> enumSerializer) {
            this.enumSerializer = enumSerializer;
            this.argsSerializer = new ListSerializer<Expression>(this);
        }

        @Override
        public Expression read(Decoder decoder) throws Exception {
            byte tag = decoder.readByte();
            if (tag == SIMPLE) {
                String expressionValue = decoder.readNullableString();
                IncludeType expressionType = enumSerializer.read(decoder);
                return new SimpleExpression(expressionValue, expressionType);
            } else if (tag == EMPTY_TOKENS) {
                return SimpleExpression.EMPTY_EXPRESSIONS;
            } else if (tag == EMPTY_ARGS) {
                return SimpleExpression.EMPTY_ARGS;
            } else if (tag == COMMA) {
                return SimpleExpression.COMMA;
            } else if (tag == LEFT_PAREN) {
                return SimpleExpression.LEFT_PAREN;
            } else if (tag == RIGHT_PAREN) {
                return SimpleExpression.RIGHT_PAREN;
            } else if (tag == COMPLEX_ONE_ARG) {
                String expressionValue = decoder.readNullableString();
                IncludeType type = enumSerializer.read(decoder);
                List<Expression> args = ImmutableList.of(read(decoder));
                return new ComplexExpression(type, expressionValue, args);
            } else if (tag == COMPLEX_MULTIPLE_ARGS) {
                String expressionValue = decoder.readNullableString();
                IncludeType type = enumSerializer.read(decoder);
                List<Expression> args = ImmutableList.copyOf(argsSerializer.read(decoder));
                return new ComplexExpression(type, expressionValue, args);
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public void write(Encoder encoder, Expression value) throws Exception {
            if (value instanceof SimpleExpression) {
                if (value.equals(SimpleExpression.EMPTY_EXPRESSIONS)) {
                    encoder.writeByte(EMPTY_TOKENS);
                } else if (value.equals(SimpleExpression.EMPTY_ARGS)) {
                    encoder.writeByte(EMPTY_ARGS);
                } else if (value.equals(SimpleExpression.COMMA)) {
                    encoder.writeByte(COMMA);
                } else if (value.equals(SimpleExpression.LEFT_PAREN)) {
                    encoder.writeByte(LEFT_PAREN);
                } else if (value.equals(SimpleExpression.RIGHT_PAREN)) {
                    encoder.writeByte(RIGHT_PAREN);
                } else {
                    encoder.writeByte(SIMPLE);
                    encoder.writeNullableString(value.getValue());
                    enumSerializer.write(encoder, value.getType());
                }
            } else if (value instanceof ComplexExpression) {
                if (value.getArguments().size() == 1) {
                    encoder.writeByte(COMPLEX_ONE_ARG);
                    encoder.writeNullableString(value.getValue());
                    enumSerializer.write(encoder, value.getType());
                    write(encoder, value.getArguments().get(0));
                } else {
                    encoder.writeByte(COMPLEX_MULTIPLE_ARGS);
                    encoder.writeNullableString(value.getValue());
                    enumSerializer.write(encoder, value.getType());
                    argsSerializer.write(encoder, value.getArguments());
                }
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    private static class IncludeSerializer implements Serializer<Include> {
        private final Serializer<IncludeType> enumSerializer;
        private final Serializer<Expression> expressionSerializer;

        private IncludeSerializer(Serializer<IncludeType> enumSerializer, Serializer<Expression> expressionSerializer) {
            this.enumSerializer = enumSerializer;
            this.expressionSerializer = expressionSerializer;
        }

        @Override
        public Include read(Decoder decoder) throws Exception {
            String value = decoder.readString();
            boolean isImport = decoder.readBoolean();
            IncludeType type = enumSerializer.read(decoder);
            int argsCount = decoder.readSmallInt();
            if (argsCount == 0) {
                return IncludeWithSimpleExpression.create(value, isImport, type);
            }
            List<Expression> args = new ArrayList<Expression>(argsCount);
            for (int i = 0; i < argsCount; i++) {
                args.add(expressionSerializer.read(decoder));
            }
            return new IncludeWithMacroFunctionCallExpression(value, isImport, ImmutableList.copyOf(args));
        }

        @Override
        public void write(Encoder encoder, Include value) throws Exception {
            encoder.writeString(value.getValue());
            encoder.writeBoolean(value.isImport());
            enumSerializer.write(encoder, value.getType());
            if (value instanceof IncludeWithSimpleExpression) {
                encoder.writeSmallInt(0);
            } else if (value instanceof IncludeWithMacroFunctionCallExpression) {
                encoder.writeSmallInt(value.getArguments().size());
                for (Expression expression : value.getArguments()) {
                    expressionSerializer.write(encoder, expression);
                }
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    private static class MacroSerializer implements Serializer<Macro> {
        private static final byte SIMPLE = (byte) 1;
        private static final byte COMPLEX = (byte) 2;
        private static final byte UNRESOLVED = (byte) 3;
        private final Serializer<IncludeType> enumSerializer;
        private final Serializer<List<Expression>> expressionSerializer;

        MacroSerializer(Serializer<IncludeType> enumSerializer, Serializer<Expression> expressionSerializer) {
            this.enumSerializer = enumSerializer;
            this.expressionSerializer = new ListSerializer<Expression>(expressionSerializer);
        }

        @Override
        public Macro read(Decoder decoder) throws Exception {
            byte tag = decoder.readByte();
            if (tag == SIMPLE) {
                String name = decoder.readString();
                IncludeType type = enumSerializer.read(decoder);
                String value = decoder.readNullableString();
                return new MacroWithSimpleExpression(name, type, value);
            } else if (tag == COMPLEX) {
                String name = decoder.readString();
                IncludeType type = enumSerializer.read(decoder);
                String value = decoder.readNullableString();
                List<Expression> args = ImmutableList.copyOf(expressionSerializer.read(decoder));
                return new MacroWithComplexExpression(name, type, value, args);
            } else if (tag == UNRESOLVED) {
                String name = decoder.readString();
                return new UnresolvableMacro(name);
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public void write(Encoder encoder, Macro value) throws Exception {
            if (value instanceof MacroWithSimpleExpression) {
                encoder.writeByte(SIMPLE);
                encoder.writeString(value.getName());
                enumSerializer.write(encoder, value.getType());
                encoder.writeNullableString(value.getValue());
            } else if (value instanceof MacroWithComplexExpression) {
                encoder.writeByte(COMPLEX);
                encoder.writeString(value.getName());
                enumSerializer.write(encoder, value.getType());
                encoder.writeNullableString(value.getValue());
                expressionSerializer.write(encoder, value.getArguments());
            } else if (value instanceof UnresolvableMacro) {
                encoder.writeByte(UNRESOLVED);
                encoder.writeString(value.getName());
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    private static class MacroFunctionSerializer implements Serializer<MacroFunction> {
        private static final byte FIXED_VALUE = (byte) 1;
        private static final byte RETURN_PARAM = (byte) 2;
        private static final byte MAPPING = (byte) 3;
        private static final byte UNRESOLVED = (byte) 4;
        private final Serializer<IncludeType> enumSerializer;
        private final Serializer<List<Expression>> expressionSerializer;

        MacroFunctionSerializer(Serializer<IncludeType> enumSerializer, Serializer<Expression> expressionSerializer) {
            this.enumSerializer = enumSerializer;
            this.expressionSerializer = new ListSerializer<Expression>(expressionSerializer);
        }

        @Override
        public MacroFunction read(Decoder decoder) throws Exception {
            byte tag = decoder.readByte();
            if (tag == FIXED_VALUE) {
                String name = decoder.readString();
                int parameters = decoder.readSmallInt();
                IncludeType type = enumSerializer.read(decoder);
                String value = decoder.readNullableString();
                List<Expression> args = ImmutableList.copyOf(expressionSerializer.read(decoder));
                return new ReturnFixedValueMacroFunction(name, parameters, type, value, args);
            } else if (tag == RETURN_PARAM) {
                String name = decoder.readString();
                int parameters = decoder.readSmallInt();
                int parameterToReturn = decoder.readSmallInt();
                return new ReturnParameterMacroFunction(name, parameters, parameterToReturn);
            } else if (tag == MAPPING) {
                String name = decoder.readString();
                int parameters = decoder.readSmallInt();
                IncludeType type = enumSerializer.read(decoder);
                String value = decoder.readNullableString();
                List<Expression> arguments = ImmutableList.copyOf(expressionSerializer.read(decoder));
                int mapCount = decoder.readSmallInt();
                int[] argsMap = new int[mapCount];
                for (int i = 0; i < mapCount; i++) {
                    argsMap[i] = decoder.readSmallInt();
                }
                return new ArgsMappingMacroFunction(name, parameters, argsMap, type, value, arguments);
            } else if (tag == UNRESOLVED) {
                String name = decoder.readString();
                int parameters = decoder.readSmallInt();
                return new UnresolvableMacroFunction(name, parameters);
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public void write(Encoder encoder, MacroFunction value) throws Exception {
            if (value instanceof ReturnFixedValueMacroFunction) {
                ReturnFixedValueMacroFunction fixedValueFunction = (ReturnFixedValueMacroFunction) value;
                encoder.writeByte(FIXED_VALUE);
                encoder.writeString(value.getName());
                encoder.writeSmallInt(value.getParameterCount());
                enumSerializer.write(encoder, fixedValueFunction.getType());
                encoder.writeNullableString(fixedValueFunction.getValue());
                expressionSerializer.write(encoder, fixedValueFunction.getArguments());
            } else if (value instanceof ReturnParameterMacroFunction) {
                ReturnParameterMacroFunction returnParameterFunction = (ReturnParameterMacroFunction) value;
                encoder.writeByte(RETURN_PARAM);
                encoder.writeString(value.getName());
                encoder.writeSmallInt(value.getParameterCount());
                encoder.writeSmallInt(returnParameterFunction.getParameterToReturn());
            } else if (value instanceof ArgsMappingMacroFunction) {
                ArgsMappingMacroFunction argsMappingFunction = (ArgsMappingMacroFunction) value;
                encoder.writeByte(MAPPING);
                encoder.writeString(value.getName());
                encoder.writeSmallInt(value.getParameterCount());
                enumSerializer.write(encoder, argsMappingFunction.getType());
                encoder.writeNullableString(argsMappingFunction.getValue());
                expressionSerializer.write(encoder, argsMappingFunction.getArguments());
                int[] argsMap = argsMappingFunction.getArgsMap();
                encoder.writeSmallInt(argsMap.length);
                for (int anArgsMap : argsMap) {
                    encoder.writeSmallInt(anArgsMap);
                }
            } else if (value instanceof UnresolvableMacroFunction) {
                encoder.writeByte(UNRESOLVED);
                encoder.writeString(value.getName());
                encoder.writeSmallInt(value.getParameterCount());
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    private static class CollectionSerializer<T> extends AbstractCollectionSerializer<T, Collection<T>> {
        CollectionSerializer(Serializer<T> entrySerializer) {
            super(entrySerializer);
        }

        @Override
        protected Collection<T> createCollection(int size) {
            return new ArrayList<T>(size);
        }
    }
}
