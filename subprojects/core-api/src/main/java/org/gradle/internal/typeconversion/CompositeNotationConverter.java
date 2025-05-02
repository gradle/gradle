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

import java.util.List;

public class CompositeNotationConverter<N, T> implements NotationConverter<N, T> {
    private final List<NotationConverter<? super N, ? extends T>> converters;

    public CompositeNotationConverter(List<NotationConverter<? super N, ? extends T>> converters) {
        this.converters = converters;
    }

    @Override
    public void convert(N notation, NotationConvertResult<? super T> result) throws TypeConversionException {
        for (int i = 0; !result.hasResult() && i < converters.size(); i++) {
            NotationConverter<? super N, ? extends T> converter = converters.get(i);
            converter.convert(notation, result);
        }
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        for (NotationConverter<? super N, ? extends T> converter : converters) {
            converter.describe(visitor);
        }
    }
}
