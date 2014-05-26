/*
 * Copyright 2011 the original author or authors.
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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class NotationParserBuilder<T> {
    private TypeInfo<T> resultingType;
    private String invalidNotationMessage;
    private final Collection<NotationConverter<Object, ? extends T>> notationParsers = new LinkedList<NotationConverter<Object, ? extends T>>();
    private boolean withJustReturningParser = true;

    public static <T> NotationParserBuilder<T> toType(Class<T> resultingType) {
        return new NotationParserBuilder<T>(new TypeInfo<T>(resultingType));
    }

    public static <T> NotationParserBuilder<T> toType(TypeInfo<T> resultingType) {
        return new NotationParserBuilder<T>(resultingType);
    }

    public NotationParserBuilder(TypeInfo<T> resultingType) {
        this.resultingType = resultingType;
    }

    public NotationParserBuilder<T> withDefaultJustReturnParser(boolean withJustReturningParser) {
        this.withJustReturningParser = withJustReturningParser;
        return this;
    }

    public NotationParserBuilder<T> parser(NotationParser<Object, ? extends T> parser) {
        this.notationParsers.add(new NotationParserToNotationConverterAdapter<Object, T>(parser));
        return this;
    }

    /**
     * Adds a converter to use to parse notations. Converters are used in the order added.
     */
    public NotationParserBuilder<T> converter(NotationConverter<Object, ? extends T> converter) {
        this.notationParsers.add(converter);
        return this;
    }

    /**
     * Adds a converter that accepts only notations of the given type.
     */
    public <S> NotationParserBuilder<T> fromType(Class<S> notationType, NotationConverter<? super S, ? extends T> converter) {
        this.notationParsers.add(new TypeFilteringNotationConverter<Object, S, T>(notationType, converter));
        return this;
    }

    /**
     * Adds a converter that accepts any CharSequence notation.
     */
    public NotationParserBuilder<T> fromCharSequence(NotationConverter<String, ? extends T> converter) {
        this.notationParsers.add(new CharSequenceNotationConverter<Object, T>(converter));
        return this;
    }

    /**
     * Adds a converter that accepts any CharSequence notation. Can only be used when the target type is String.
     */
    public NotationParserBuilder<T> fromCharSequence() {
        if (!resultingType.getTargetType().equals(String.class)) {
            throw new UnsupportedOperationException("Can only convert from CharSequence when the target type is String.");
        }
        NotationConverter notationParser = new CharSequenceNotationParser();
        fromCharSequence(notationParser);
        return this;
    }

    public NotationParserBuilder<T> invalidNotationMessage(String invalidNotationMessage) {
        this.invalidNotationMessage = invalidNotationMessage;
        return this;
    }

    public NotationParserBuilder<T> parsers(Iterable<? extends NotationParser<Object, ? extends T>> notationParsers) {
        for (NotationParser<Object, ? extends T> parser : notationParsers) {
            parser(parser);
        }
        return this;
    }

    public NotationParser<Object, Set<T>> toFlatteningComposite() {
        return wrapInErrorHandling(new FlatteningNotationParser<T>(create()));
    }

    public NotationParser<Object, T> toComposite() {
        return wrapInErrorHandling(create());
    }

    private <S> NotationParser<Object, S> wrapInErrorHandling(NotationParser<Object, S> parser) {
        return new ErrorHandlingNotationParser<Object, S>(resultingType.getTargetType().getSimpleName(), invalidNotationMessage, parser);
    }

    private NotationParser<Object, T> create() {
        assert resultingType != null : "resultingType cannot be null";

        List<NotationConverter<Object, ? extends T>> composites = new LinkedList<NotationConverter<Object, ? extends T>>();
        if (withJustReturningParser) {
            composites.add(new NotationParserToNotationConverterAdapter<Object, T>(new JustReturningParser<Object, T>(resultingType.getTargetType())));
        }
        composites.addAll(this.notationParsers);

        return new NotationConverterToNotationParserAdapter<Object, T>(new CompositeNotationConverter<Object, T>(composites));
    }

    private static class NotationParserToNotationConverterAdapter<N, T> implements NotationConverter<N, T> {
        private final NotationParser<N, ? extends T> parser;

        private NotationParserToNotationConverterAdapter(NotationParser<N, ? extends T> parser) {
            this.parser = parser;
        }

        public void convert(N notation, NotationConvertResult<? super T> result) throws TypeConversionException {
            T t;
            try {
                t = parser.parseNotation(notation);
            } catch (UnsupportedNotationException e) {
                return;
            }
            result.converted(t);
        }

        public void describe(Collection<String> candidateFormats) {
            parser.describe(candidateFormats);
        }
    }
}