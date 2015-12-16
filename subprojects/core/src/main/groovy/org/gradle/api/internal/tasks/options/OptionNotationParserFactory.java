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

import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.CompositeNotationParser;
import org.gradle.internal.typeconversion.EnumFromCharSequenceNotationParser;
import org.gradle.internal.typeconversion.NotationParser;

import java.util.ArrayList;
import java.util.List;

public class OptionNotationParserFactory {
    public NotationParser<CharSequence, Object> toComposite(Class<?> targetType) throws OptionValidationException {
        assert targetType != null : "resultingType cannot be null";
        List<NotationParser<CharSequence, ?>> parsers = new ArrayList<NotationParser<CharSequence, ?>>();

        if (targetType == Void.TYPE) {
            parsers.add(new UnsupportedNotationParser());
        }
        if (targetType.isAssignableFrom(String.class)) {
            parsers.add(new NoDescriptionValuesJustReturningParser());
        }
        if (targetType.isEnum()) {
            parsers.add(new EnumFromCharSequenceNotationParser<Enum>(targetType.asSubclass(Enum.class)));
        }
        if (parsers.isEmpty()) {
            throw new OptionValidationException(String.format("Don't know how to convert strings to type '%s'.", targetType.getName()));
        }
        return new CompositeNotationParser<CharSequence, Object>(parsers);
    }

    private static class UnsupportedNotationParser implements NotationParser<CharSequence, Object> {

        public Object parseNotation(CharSequence notation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
        }

    }

    private static class NoDescriptionValuesJustReturningParser implements NotationParser<CharSequence, Object> {
        public String parseNotation(CharSequence notation) {
            return notation.toString();
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Instances of String or CharSequence.");
        }

    }
}
