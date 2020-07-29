/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.internal.exceptions.DiagnosticsVisitor;

public class NotationConverterToNotationParserAdapter<N, T> implements NotationParser<N, T> {
    private final NotationConverter<? super N, ? extends T> converter;

    public NotationConverterToNotationParserAdapter(NotationConverter<? super N, ? extends T> converter) {
        this.converter = converter;
    }

    @Override
    public T parseNotation(N notation) throws TypeConversionException {
        ResultImpl<T> result = new ResultImpl<>();
        converter.convert(notation, result);
        if (!result.hasResult) {
            throw new UnsupportedNotationException(notation);
        }
        return result.result;
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        converter.describe(visitor);
    }

    private static class ResultImpl<T> implements NotationConvertResult<T> {
        private boolean hasResult;
        private T result;

        @Override
        public boolean hasResult() {
            return hasResult;
        }

        @Override
        public void converted(T result) {
            hasResult = true;
            this.result = result;
        }
    }
}
