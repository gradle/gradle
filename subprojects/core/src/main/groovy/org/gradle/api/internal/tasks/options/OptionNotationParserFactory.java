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
    public ValueAwareNotationParser<Object> toComposite(Class<?> targetType) throws OptionValidationException {
        assert targetType != null : "resultingType cannot be null";
        List<ValueAwareNotationParser<?>> parsers = new ArrayList<ValueAwareNotationParser<?>>();

        if (targetType == Void.TYPE) {
            parsers.add(new UnsupportedNotationParser());
        }
        if (targetType.isAssignableFrom(String.class)) {
            parsers.add(new NoDescriptionValuesJustReturningParser<Object>(targetType));
        }
        if (targetType.isEnum()) {
            parsers.add(new NoDescriptionValuesJustReturningParser<Object>(targetType));
            parsers.add(new EnumFromCharSequenceNotationParser<Enum>(targetType.asSubclass(Enum.class)));
        }
        if (parsers.isEmpty()) {
            throw new OptionValidationException(String.format("Don't know how to convert strings to type '%s'.", targetType.getName()));
        }
        return new ValueAwareCompositeNotationParser<Object>(parsers);
    }

    private static class UnsupportedNotationParser implements ValueAwareNotationParser<Object> {

        public Object parseNotation(CharSequence notation) throws UnsupportedNotationException, TypeConversionException {
            throw new UnsupportedOperationException();
        }

        public void describe(Collection<String> candidateFormats) {
        }

        public void describeValues(Collection<String> collector) {
        }
    }

    private static class NoDescriptionValuesJustReturningParser<T> extends JustReturningParser<CharSequence, T> implements ValueAwareNotationParser<T> {
        public NoDescriptionValuesJustReturningParser(Class<? extends T> targetType) {
            super(targetType);
        }

        public void describeValues(Collection<String> collector) {

        }
    }

    private static class ValueAwareCompositeNotationParser<T> extends CompositeNotationParser<CharSequence, T> implements ValueAwareNotationParser<T> {
        private final Collection<ValueAwareNotationParser<? extends T>> delegates;

        public ValueAwareCompositeNotationParser(Collection<ValueAwareNotationParser<? extends T>> delegates) {
            super(delegates);
            this.delegates = delegates;
        }

        public void describeValues(Collection<String> collector) {
            for (ValueAwareNotationParser<? extends T> delegate : delegates) {
                delegate.describeValues(collector);
            }
        }
    }
}
