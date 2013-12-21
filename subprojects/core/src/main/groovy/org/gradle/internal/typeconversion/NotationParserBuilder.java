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

import org.gradle.util.GUtil;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class NotationParserBuilder<T> {
    private TypeInfo<T> resultingType;
    private String invalidNotationMessage;
    private Collection<NotationParser<Object, ? extends T>> notationParsers = new LinkedList<NotationParser<Object, ? extends T>>();
    private boolean withJustReturningParser = true;

    public NotationParserBuilder<T> resultingType(Class<T> resultingType) {
        return resultingType(new TypeInfo<T>(resultingType));
    }

    public NotationParserBuilder<T> resultingType(TypeInfo<T> resultingType) {
        this.resultingType = resultingType;
        return this;
    }

    public NotationParserBuilder<T> withDefaultJustReturnParser(boolean withJustReturningParser) {
        this.withJustReturningParser = withJustReturningParser;
        return this;
    }

    public NotationParserBuilder<T> parser(NotationParser<Object, ? extends T> parser) {
        this.notationParsers.add(parser);
        return this;
    }

    public NotationParserBuilder<T> invalidNotationMessage(String invalidNotationMessage) {
        this.invalidNotationMessage = invalidNotationMessage;
        return this;
    }

    public NotationParserBuilder<T> parsers(Iterable<? extends NotationParser<Object, ? extends T>> notationParsers) {
        GUtil.addToCollection(this.notationParsers, notationParsers);
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

    private CompositeNotationParser<Object, T> create() {
        assert resultingType != null : "resultingType cannot be null";

        List<NotationParser<Object, ? extends T>> composites = new LinkedList<NotationParser<Object, ? extends T>>();
        if(withJustReturningParser){
            composites.add(new JustReturningParser<Object, T>(resultingType.getTargetType()));
        }
        composites.addAll(this.notationParsers);

        return new CompositeNotationParser<Object, T>(composites);
    }
}