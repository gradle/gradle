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

import java.util.Collection;

class CharSequenceNotationConverter<N, T> implements NotationConverter<N, T> {
    private final NotationConverter<String, ? extends T> delegate;

    public CharSequenceNotationConverter(NotationConverter<String, ? extends T> delegate) {
        this.delegate = delegate;
    }

    public void convert(N notation, NotationConvertResult<? super T> result) throws TypeConversionException {
        if (notation instanceof CharSequence) {
            CharSequence charSequence = (CharSequence) notation;
            delegate.convert(charSequence.toString(), result);
        }
    }

    public void describe(Collection<String> candidateFormats) {
        delegate.describe(candidateFormats);
    }
}
