/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.file.copy;

import org.gradle.api.provider.Provider;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeConversionException;

import java.io.File;
import java.util.concurrent.Callable;

import static org.gradle.internal.UncheckedException.uncheckedCall;

public class PathNotationConverter implements NotationConverter<Object, String> {

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("String or CharSequence instances").example("\"some/path\"");
        visitor.candidate("Boolean values").example("true").example("Boolean.TRUE");
        visitor.candidate("Number values").example("42").example("3.14");
        visitor.candidate("A File instance");
        visitor.candidate("A Closure that returns any supported value.");
        visitor.candidate("A Callable that returns any supported value.");
        visitor.candidate("A Provider that provides any supported value.");
    }

    public static NotationParser<Object, String> parser() {
        return NotationParserBuilder
                .toType(String.class)
                .noImplicitConverters()
                .allowNullInput()
                .converter(new PathNotationConverter())
                .toComposite();
    }

    @Override
    public void convert(Object notation, NotationConvertResult<? super String> result) throws TypeConversionException {
        if (notation == null) {
            result.converted(null);
        } else if (notation instanceof CharSequence
                || notation instanceof File
                || notation instanceof Number
                || notation instanceof Boolean) {
            result.converted(notation.toString());
        } else if (notation instanceof Callable) {
            final Callable<?> callableNotation = (Callable<?>) notation;
            final Object called = uncheckedCall(callableNotation);
            convert(called, result);
        } else if (notation instanceof Provider) {
            final Provider<?> providerNotation = (Provider<?>) notation;
            final Object called = providerNotation.get();
            convert(called, result);
        }
    }
}
