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

package org.gradle.api.tasks.diagnostics.internal.dsl;

import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeInfo;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.UnsupportedNotationException;
import org.gradle.internal.typeconversion.ClosureToSpecNotationParser;
import org.gradle.api.specs.Spec;

import java.util.Collection;

public class DependencyResultSpecNotationParser implements NotationParser<Object, Spec<DependencyResult>> {

    public Spec<DependencyResult> parseNotation(final Object notation) throws UnsupportedNotationException {
        if (notation instanceof CharSequence) {
            final String stringNotation = notation.toString().trim();
            if (stringNotation.length() > 0) {
                return new DependencyResultSpec(stringNotation);
            }
        }
        throw new UnsupportedNotationException(notation);
    }

    public void describe(Collection<String> candidateFormats) {
        candidateFormats.add("Non-empty String value, e.g. 'some-lib' or 'org.libs:some-lib'.");
        candidateFormats.add("Closure that returns boolean and takes a single DependencyResult as parameter.");
    }

    public static NotationParser<Object, Spec<DependencyResult>> create() {
        return new NotationParserBuilder<Spec<DependencyResult>>()
                .resultingType(new TypeInfo<Spec<DependencyResult>>(Spec.class))
                .invalidNotationMessage("Please check the input for the DependencyInsight.dependency element.")
                .parser(new ClosureToSpecNotationParser<DependencyResult>())
                .parser(new DependencyResultSpecNotationParser())
                .toComposite();
    }
}