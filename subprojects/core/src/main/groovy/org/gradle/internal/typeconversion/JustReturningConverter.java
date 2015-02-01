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

public class JustReturningConverter<N, T> implements NotationConverter<N, T> {

    private final Class<? extends T> passThroughType;

    public JustReturningConverter(Class<? extends T> passThroughType) {
        this.passThroughType = passThroughType;
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate(String.format("Instances of %s.", passThroughType.getSimpleName()));
    }

    @Override
    public void convert(N notation, NotationConvertResult<? super T> result) throws TypeConversionException {
        if (passThroughType.isInstance(notation)) {
            result.converted(passThroughType.cast(notation));
        }
    }
}