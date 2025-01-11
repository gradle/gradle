/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.gradle.api.Transformer;
import org.gradle.internal.exceptions.DiagnosticsVisitor;

@NonNullApi
public class TransformingConverter<N, T, R> implements NotationConverter<N, R> {
    private final NotationConverter<N, T> converter;
    private final Transformer<? extends R, T> transformer;

    public TransformingConverter(NotationConverter<N, T> converter, Transformer<? extends R, T> transformer) {
        this.converter = converter;
        this.transformer = transformer;
    }

    @Override
    public void convert(N notation, NotationConvertResult<? super R> result) throws TypeConversionException {
        ResultImpl<T> intermediateResult = new ResultImpl<>();
        converter.convert(notation, intermediateResult);
        if (intermediateResult.hasResult()) {
            result.converted(transformer.transform(intermediateResult.result));
        }
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        converter.describe(visitor);
    }


    @NonNullApi
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
