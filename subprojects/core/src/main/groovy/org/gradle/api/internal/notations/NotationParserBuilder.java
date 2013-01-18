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

package org.gradle.api.internal.notations;

import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.api.internal.notations.parsers.*;
import org.gradle.util.GUtil;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * by Szczepan Faber, created at: 11/8/11
 */
public class NotationParserBuilder<T> {
    private TypeInfo<T> resultingType;
    private String invalidNotationMessage;
    private Collection<NotationParser<? extends T>> notationParsers = new LinkedList<NotationParser<? extends T>>();
    private boolean nullUnsupported;

    public NotationParserBuilder<T> resultingType(Class<T> resultingType) {
        return resultingType(new TypeInfo<T>(resultingType));
    }

    public NotationParserBuilder<T> resultingType(TypeInfo<T> resultingType) {
        this.resultingType = resultingType;
        return this;
    }

    public NotationParserBuilder<T> parser(NotationParser<? extends T> parser) {
        this.notationParsers.add(parser);
        return this;
    }

    public NotationParserBuilder<T> invalidNotationMessage(String invalidNotationMessage) {
        this.invalidNotationMessage = invalidNotationMessage;
        return this;
    }

    public NotationParserBuilder<T> parsers(Iterable<? extends NotationParser<? extends T>> notationParsers) {
        GUtil.addToCollection(this.notationParsers, notationParsers);
        return this;
    }

    public NotationParser<Set<T>> toFlatteningComposite() {
        return wrapInErrorHandling(new FlatteningNotationParser<T>(create()));
    }

    public NotationParser<T> toComposite() {
        return wrapInErrorHandling(create());
    }

    private <S> NotationParser<S> wrapInErrorHandling(NotationParser<S> parser) {
        return new ErrorHandlingNotationParser<S>(resultingType.getTargetType().getSimpleName(), invalidNotationMessage, parser, nullUnsupported);
    }

    private CompositeNotationParser<T> create() {
        assert resultingType != null : "resultingType cannot be null";

        List<NotationParser<? extends T>> composites = new LinkedList<NotationParser<? extends T>>();
        composites.add(new JustReturningParser<T>(resultingType.getTargetType()));
        composites.addAll(this.notationParsers);

        return new CompositeNotationParser<T>(composites);
    }

    public NotationParserBuilder<T> nullUnsupported() {
        this.nullUnsupported = true;
        return this;
    }
}