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

package org.gradle.api.internal.tasks.options;

import org.gradle.internal.typeconversion.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class OptionNotationParserFactory {
    public NotationParser<String, Object> toComposite(Class<?> targetType) {
        assert targetType != null : "resultingType cannot be null";
        List<NotationParser<String, Object>> parsers = new ArrayList<NotationParser<String, Object>>();

        if (targetType == Void.TYPE) {
            parsers.add(new UnsupportedNotationParser());
        }
        if (targetType.isAssignableFrom(String.class)) {
            parsers.add(new NoDescriptionJustReturningParser(targetType));
        }
        if (targetType.isEnum()) {
            parsers.add(new NoDescriptionJustReturningParser(targetType));
            parsers.add(new EnumFromStringNotationParser<Enum>(targetType.asSubclass(Enum.class)));
        }
        if (parsers.isEmpty()) {
            throw new OptionValidationException(String.format("resultingType '%s' not supported", targetType.getName()));
        }
        return new CompositeNotationParser<String, Object>(parsers);
    }

    private class UnsupportedNotationParser implements NotationParser<String, Object> {
        public Object parseNotation(String notation) throws UnsupportedNotationException, TypeConversionException {
            throw new UnsupportedOperationException();
        }

        public void describe(Collection<String> candidateFormats) {
        }
    }

    private class NoDescriptionJustReturningParser extends JustReturningParser<String, Object> {
        public NoDescriptionJustReturningParser(Class<?> targetType) {
            super(targetType);
        }

        public void describe(Collection<String> candidateFormats) {

        }
    }
}
