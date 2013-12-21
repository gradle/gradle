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

package org.gradle.internal.typeconversion;

import groovy.lang.Closure;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;

import java.util.Collection;

public class ClosureToSpecNotationParser<T> implements NotationParser<Object, Spec<T>> {
    public Spec<T> parseNotation(Object notation) throws UnsupportedNotationException {
        if (notation instanceof Closure) {
            return Specs.convertClosureToSpec((Closure) notation);
        }
        throw new UnsupportedNotationException(notation);
    }

    public void describe(Collection<String> candidateFormats) {
        candidateFormats.add("Closure that returns boolean. See the DSL reference for information what parameters are passed into the closure.");
    }
}
