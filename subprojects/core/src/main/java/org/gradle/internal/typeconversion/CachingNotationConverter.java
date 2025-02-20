/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.internal.model.InMemoryCacheFactory;
import org.gradle.internal.model.InMemoryLoadingCache;

/**
 * A {@link NotationConverter} that caches the result of conversion.
 */
public class CachingNotationConverter<T> implements NotationConverter<Object, T> {

    private final InMemoryLoadingCache<Object, T> cache;
    private final NotationConverterToNotationParserAdapter<Object, T> delegate;

    public CachingNotationConverter(NotationConverter<Object, T> notationConverter, InMemoryCacheFactory cacheFactory) {
        this.delegate = new NotationConverterToNotationParserAdapter<>(notationConverter);
        this.cache = cacheFactory.create(delegate::parseNotation);
    }

    @Override
    public void convert(Object notation, NotationConvertResult<? super T> result) throws TypeConversionException {
        result.converted(cache.get(notation));
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        delegate.describe(visitor);
    }

}
