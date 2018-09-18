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

import org.gradle.internal.Cast;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.EnumFromCharSequenceNotationParser;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.NotationConverterToNotationParserAdapter;
import org.gradle.internal.typeconversion.NotationParser;

public class OptionValueNotationParserFactory {
    public <T> NotationParser<CharSequence, T> toComposite(Class<T> targetType) throws OptionValidationException {
        assert targetType != null : "resultingType cannot be null";
        if (targetType == Void.TYPE) {
            return new UnsupportedNotationParser<T>();
        } else if (targetType.isAssignableFrom(String.class) || targetType == java.util.List.class) {
            return Cast.uncheckedCast(new NoDescriptionValuesJustReturningParser());
        } else if (targetType.isEnum()) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            NotationConverter<CharSequence, T> converter = new EnumFromCharSequenceNotationParser(targetType.asSubclass(Enum.class));
            return new NotationConverterToNotationParserAdapter<CharSequence, T>(converter);
        }

        throw new OptionValidationException(String.format("Don't know how to convert strings to type '%s'.", targetType.getName()));
    }

    private static class UnsupportedNotationParser<T> implements NotationParser<CharSequence, T> {

        public T parseNotation(CharSequence notation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
        }

    }

    private static class NoDescriptionValuesJustReturningParser implements NotationParser<CharSequence, String> {
        public String parseNotation(CharSequence notation) {
            return notation.toString();
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Instances of String or CharSequence.");
        }

    }
}
