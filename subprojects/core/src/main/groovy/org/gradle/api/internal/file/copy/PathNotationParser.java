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
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.UncheckedException;
import org.gradle.util.DeprecationLogger;

import java.util.Collection;
import java.util.concurrent.Callable;

public class PathNotationParser<T extends String> implements NotationParser<Object, T> {

    public void describe(Collection<String> candidateFormats) {
        candidateFormats.add("Strings, Boolean, Number like: 'path/to', true, Boolean.TRUE, 42, 3.14");
        candidateFormats.add("Closures, Callables");
    }

    public T parseNotation(Object notation) {
        if (notation == null) {
            return null;
        }
        if (notation instanceof CharSequence
                || notation instanceof Number
                || notation instanceof Boolean) {
            return (T) notation.toString();
        }
        if (notation instanceof Closure) {
            final Closure closure = (Closure) notation;
            final Object called = closure.call();
            return parseNotation(called);
        }
        if (notation instanceof Callable) {
            try {
                final Callable callableNotation = (Callable) notation;
                final Object called = callableNotation.call();
                return parseNotation(called);
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
        DeprecationLogger.nagUserOfDeprecated(
                String.format("Converting class %s to path using toString() method", notation.getClass().getName()),
                "Please use java.io.File, java.lang.CharSequence, java.lang.Number, java.util.concurrent.Callable or groovy.lang.Closure"
        );
        return (T) notation.toString();
    }
}
