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

import groovy.lang.Closure;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.typeconversion.*;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.Callable;

public class PathNotationConverter implements NotationConverter<Object, String> {

    public void describe(Collection<String> candidateFormats) {
        candidateFormats.add("String or CharSequence instances e.g. 'some/path'");
        candidateFormats.add("Boolean values e.g. true, Boolean.TRUE");
        candidateFormats.add("Number values e.g. 42, 3.14");
        candidateFormats.add("A File instance");
        candidateFormats.add("A Closure that returns any supported value.");
        candidateFormats.add("A Callable that returns any supported value.");
    }

    public static NotationParser<Object, String> create() {
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
        } else if (notation instanceof Closure) {
            final Closure closure = (Closure) notation;
            final Object called = closure.call();
            convert(called, result);
        } else if (notation instanceof Callable) {
            try {
                final Callable callableNotation = (Callable) notation;
                final Object called = callableNotation.call();
                convert(called, result);
                if (!result.hasResult()) {
                    throw new TypeConversionException("Couldn't convert " + notation);
                }
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }
}
