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
    private String typeDisplayName;
    private boolean implicitConverters = true;
    private boolean allowNullInput;
    private final Collection<NotationConverter<Object, ? extends T>> notationParsers = new LinkedList<NotationConverter<Object, ? extends T>>();

    public static <T> NotationParserBuilder<T> toType(Class<T> resultingType) {
        return new NotationParserBuilder<T>(new TypeInfo<T>(resultingType));
    }

    public static <T> NotationParserBuilder<T> toType(TypeInfo<T> resultingType) {
        return new NotationParserBuilder<T>(resultingType);
    }

    private NotationParserBuilder(TypeInfo<T> resultingType) {
        this.resultingType = resultingType;
    }

    /**
     * Specifies the display name for the target type, to use in error messages. By default the target type's simple name is used.
     */
    public NotationParserBuilder<T> typeDisplayName(String name) {
        this.typeDisplayName = name;
        return this;
    }

    /**
     * Use only those converters that are explicitly registered, and disable any implicit conversion that may normally be done.
     */
    public NotationParserBuilder<T> noImplicitConverters() {
        implicitConverters = false;
        return this;
    }

    /**
     * Allow null as a valid input. The default is to disallow null.
     *
     * <p>When this is enabled, all converters must be null safe.
     *
     * TODO - attach the null safety to each converter and infer whether null is a valid input or not.
     */
    public NotationParserBuilder<T> allowNullInput() {
        allowNullInput = true;
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
    public NotationParserBuilder<T> fromCharSequence(NotationConverter<? super String, ? extends T> converter) {
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

    public NotationParser<Object, Set<T>> toFlatteningComposite() {
        return wrapInErrorHandling(new FlatteningNotationParser<T>(create()));
    }

    public NotationParser<Object, T> toComposite() {
        return wrapInErrorHandling(create());
    }

    private <S> NotationParser<Object, S> wrapInErrorHandling(NotationParser<Object, S> parser) {
        if (typeDisplayName == null) {
            typeDisplayName = resultingType.getTargetType().equals(String.class) ? "a String" : "an object of type ".concat(resultingType.getTargetType().getSimpleName());
        }
        return new ErrorHandlingNotationParser<Object, S>(typeDisplayName, invalidNotationMessage, allowNullInput, parser);
    }

    private NotationParser<Object, T> create() {
        List<NotationConverter<Object, ? extends T>> composites = new LinkedList<NotationConverter<Object, ? extends T>>();
        if (!resultingType.getTargetType().equals(Object.class) && implicitConverters) {
            composites.add(new JustReturningConverter<Object, T>(resultingType.getTargetType()));
        }
        composites.addAll(this.notationParsers);

        return new NotationConverterToNotationParserAdapter<Object, T>(composites.size() == 1 ? composites.get(0) : new CompositeNotationConverter<Object, T>(composites));
    }
}
