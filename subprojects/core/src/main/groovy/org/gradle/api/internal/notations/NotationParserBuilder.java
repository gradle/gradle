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
import org.gradle.api.internal.notations.parsers.AlwaysThrowingParser;
import org.gradle.api.internal.notations.parsers.CompositeNotationParser;
import org.gradle.api.internal.notations.parsers.FlatteningNotationParser;
import org.gradle.api.internal.notations.parsers.JustReturningParser;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * by Szczepan Faber, created at: 11/8/11
 */
public class NotationParserBuilder {
    private Class resultingType;
    private String invalidNotationMessage;
    private Collection<NotationParser> notationParsers = new LinkedList<NotationParser>();

    public NotationParserBuilder resultingType(Class resultingType) {
        this.resultingType = resultingType;
        return this;
    }

    public NotationParserBuilder parser(NotationParser parser) {
        this.notationParsers.add(parser);
        return this;
    }

    public NotationParserBuilder invalidNotationMessage(String invalidNotationMessage) {
        this.invalidNotationMessage = invalidNotationMessage;
        return this;
    }

    public NotationParserBuilder parsers(Set<NotationParser> notationParsers) {
        this.notationParsers.addAll(notationParsers);
        return this;
    }

    public <T> NotationParser<Set<T>> build() {
        assert invalidNotationMessage != null : "invalidNotationMessage cannot be null";

        List composites = new LinkedList();
        if (resultingType != null) {
            composites.add(new JustReturningParser(resultingType));
        }
        composites.addAll(this.notationParsers);
        composites.add(new AlwaysThrowingParser(invalidNotationMessage));

        NotationParser<T> delegate = new CompositeNotationParser<T>(composites);
        return new FlatteningNotationParser<T>(delegate);
    }
}