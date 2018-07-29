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

import org.gradle.api.Describable;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class NotationParserBuilder<N, T> {
    private final Class<N> notationType;
    private final TypeInfo<T> resultingType;
    private String invalidNotationMessage;
    private Object typeDisplayName;
    private boolean implicitConverters = true;
    private boolean allowNullInput;
    private final Collection<NotationConverter<? super N, ? extends T>> notationParsers = new LinkedList<NotationConverter<? super N, ? extends T>>();

    public static <T> NotationParserBuilder<Object, T> toType(Class<T> resultingType) {
        return new NotationParserBuilder<Object, T>(Object.class, new TypeInfo<T>(resultingType));
    }

    public static <T> NotationParserBuilder<Object, T> toType(TypeInfo<T> resultingType) {
        return new NotationParserBuilder<Object, T>(Object.class, resultingType);
    }

    public static <N, T> NotationParserBuilder<N, T> builder(Class<N> notationType, Class<T> resultingType) {
        return new NotationParserBuilder<N, T>(notationType, new TypeInfo<T>(resultingType));
    }

    private NotationParserBuilder(Class<N> notationType, TypeInfo<T> resultingType) {
        this.notationType = notationType;
        this.resultingType = resultingType;
    }

    /**
     * Specifies the display name for the target type, to use in error messages. By default the target type's simple name is used.
     */
    public NotationParserBuilder<N, T> typeDisplayName(final String name) {
        this.typeDisplayName = name;
        return this;
    }

    /**
     * Use only those converters that are explicitly registered, and disable any implicit conversion that may normally be done.
     */
    public NotationParserBuilder<N, T> noImplicitConverters() {
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
    public NotationParserBuilder<N, T> allowNullInput() {
        allowNullInput = true;
        return this;
    }

    /**
     * Adds a converter to use to parse notations. Converters are used in the order added.
     */
    public NotationParserBuilder<N, T> converter(NotationConverter<? super N, ? extends T> converter) {
        this.notationParsers.add(converter);
        return this;
    }

    /**
     * Adds a converter that accepts only notations of the given type.
     */
    public <S extends N> NotationParserBuilder<N, T> fromType(Class<S> notationType, NotationConverter<? super S, ? extends T> converter) {
        this.notationParsers.add(new TypeFilteringNotationConverter<Object, S, T>(notationType, converter));
        return this;
    }

    /**
     * Adds a converter that accepts any CharSequence notation. Can only be used the notation type is a supertype of String.
     */
    public NotationParserBuilder<N, T> fromCharSequence(NotationConverter<? super String, ? extends T> converter) {
        if (!notationType.isAssignableFrom(String.class)) {
            throw new IllegalArgumentException(String.format("Cannot convert from String when notation is %s.", notationType.getSimpleName()));
        }
        this.notationParsers.add(new CharSequenceNotationConverter<Object, T>(converter));
        return this;
    }

    /**
     * Adds a converter that accepts any CharSequence notation. Can only be used when the target type is String and the notation type is a supertype of String.
     */
    public NotationParserBuilder<N, T> fromCharSequence() {
        if (!resultingType.getTargetType().equals(String.class)) {
            throw new UnsupportedOperationException("Can only convert from CharSequence when the target type is String.");
        }
        if (!notationType.isAssignableFrom(String.class)) {
            throw new IllegalArgumentException(String.format("Cannot convert from String when notation is %s.", notationType.getSimpleName()));
        }
        NotationConverter notationParser = new CharSequenceNotationParser();
        fromCharSequence(notationParser);
        return this;
    }

    public NotationParserBuilder<N, T> invalidNotationMessage(String invalidNotationMessage) {
        this.invalidNotationMessage = invalidNotationMessage;
        return this;
    }

    public NotationParser<N, Set<T>> toFlatteningComposite() {
        return wrapInErrorHandling(new FlatteningNotationParser<N, T>(create()));
    }

    public NotationParser<N, T> toComposite() {
        return wrapInErrorHandling(create());
    }

    private <S> NotationParser<N, S> wrapInErrorHandling(NotationParser<N, S> parser) {
        if (typeDisplayName == null) {
            typeDisplayName = new LazyDisplayName<T>(resultingType);
        }
        return new ErrorHandlingNotationParser<N, S>(typeDisplayName, invalidNotationMessage, allowNullInput, parser);
    }

    private NotationParser<N, T> create() {
        List<NotationConverter<? super N, ? extends T>> composites = new LinkedList<NotationConverter<? super N, ? extends T>>();
        if (notationType.isAssignableFrom(resultingType.getTargetType()) && implicitConverters) {
            composites.add(new JustReturningConverter<N, T>(resultingType.getTargetType()));
        }
        composites.addAll(this.notationParsers);

        NotationConverter<? super N, ? extends T> notationConverter;
        if (composites.size() == 1) {
            notationConverter = composites.get(0);
        } else {
            notationConverter = new CompositeNotationConverter<N, T>(composites);
        }
        return new NotationConverterToNotationParserAdapter<N, T>(notationConverter);
    }

    private static class LazyDisplayName<T> implements Describable {
        private final TypeInfo<T> resultingType;
        private String displayName;

        public LazyDisplayName(TypeInfo<T> resultingType) {
            this.resultingType = resultingType;
        }

        @Override
        public String toString() {
            return getDisplayName();
        }

        @Override
        public String getDisplayName() {
            if (displayName == null) {
                displayName = resultingType.getTargetType().equals(String.class) ? "a String" : ("an object of type " + resultingType.getTargetType().getSimpleName());
            }
            return displayName;
        }
    }
}
