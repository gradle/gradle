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

import java.util.Arrays;
import java.util.Collection;

public class FromStringNotationParser<T extends Object> implements NotationParser<T> {
    private final NotationParser<T> delegate;
    public FromStringNotationParser(Class<T> optionType) {
        delegate = new NotationParserBuilder<T>()
                .resultingType(optionType)
                .parsers(Arrays.asList(new EnumFromStringNotationParser<T>(optionType)))
                .invalidNotationMessage(String.format("Converting String to $s", optionType))
                .toComposite();
    }

    public T parseNotation(Object notation) {
            return delegate.parseNotation(notation);
    }

    public void describe(Collection<String> candidateFormats) {
    }
}
