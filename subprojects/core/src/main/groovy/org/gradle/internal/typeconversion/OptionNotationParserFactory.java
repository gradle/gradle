/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.GradleException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class OptionNotationParserFactory<T> {

    private TypeInfo<T> resultingType;

    public OptionNotationParserFactory<T> resultingType(TypeInfo<T> resultingType) {
        this.resultingType = resultingType;
        return this;
    }

    public OptionNotationParserFactory(Class<T> optionType) {
        this.resultingType(new TypeInfo<T>(optionType));

    }

    public NotationParser<T> toComposite() {
        return create();
    }

    private NotationParser<T> create() {
        assert resultingType != null : "resultingType cannot be null";
        List<NotationParser<? extends T>> parsers = new ArrayList<NotationParser<? extends T>>();
        final Class<T> targetType = resultingType.getTargetType();

        if(targetType == Void.TYPE){
            parsers.add(new UnsupportedNotationParser());
        }
        if(targetType.isAssignableFrom(String.class)){
            parsers.add(new NoDescriptionJustReturningParser(targetType));
        }
        if (targetType.isEnum()) {
            parsers.add(new NoDescriptionJustReturningParser(targetType));
            parsers.add(new EnumFromStringNotationParser<T>(targetType));
        }
        if(parsers.isEmpty()){
            // not sure this is the right exception it should be more
            // unavailable notationparser error or something like this
            throw new GradleException(String.format("resultingType '%s' not supported", targetType.getName()));
        }
        return new CompositeNotationParser<T>(parsers);
    }

    private class UnsupportedNotationParser implements NotationParser<T> {
        public T parseNotation(Object notation) throws UnsupportedNotationException, TypeConversionException {
            throw new UnsupportedOperationException();
        }

        public void describe(Collection<String> candidateFormats) {
        }
    }

    private class NoDescriptionJustReturningParser extends JustReturningParser<T> {
        public NoDescriptionJustReturningParser(Class<T> targetType) {
            super(targetType);
        }

        public void describe(Collection<String> candidateFormats) {

        }
    }
}
