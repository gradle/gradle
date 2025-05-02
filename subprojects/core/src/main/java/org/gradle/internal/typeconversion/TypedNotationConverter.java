/*
 * Copyright 2011 the original author or authors.
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

public abstract class TypedNotationConverter<N, T> implements NotationConverter<Object, T> {

    private final Class<N> typeToken;

    public TypedNotationConverter(Class<N> typeToken) {
        assert typeToken != null : "typeToken cannot be null";
        this.typeToken = typeToken;
    }

    public TypedNotationConverter(TypeInfo<N> typeToken) {
        assert typeToken != null : "typeToken cannot be null";
        this.typeToken = typeToken.getTargetType();
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate(String.format("Instances of %s.", typeToken.getSimpleName()));
    }

    @Override
    public void convert(Object notation, NotationConvertResult<? super T> result) throws TypeConversionException {
        if (typeToken.isInstance(notation)) {
            result.converted(parseType(typeToken.cast(notation)));
        }
    }

    abstract protected T parseType(N notation);
}
