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

import groovy.lang.Closure;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.specs.Spec;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.*;

public class DependencyResultSpecNotationConverter implements NotationConverter<String, Spec<DependencyResult>> {
    @Override
    public void convert(String notation, NotationConvertResult<? super Spec<DependencyResult>> result) throws TypeConversionException {
        final String stringNotation = notation.trim();
        if (stringNotation.length() > 0) {
            result.converted(new DependencyResultSpec(stringNotation));
        }
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("Non-empty String or CharSequence value").example("'some-lib' or 'org.libs:some-lib'");
    }

    public static NotationParser<Object, Spec<DependencyResult>> parser() {
        return NotationParserBuilder
                .toType(new TypeInfo<Spec<DependencyResult>>(Spec.class))
                .invalidNotationMessage("Please check the input for the DependencyInsight.dependency element.")
                .fromType(Closure.class, new ClosureToSpecNotationConverter<DependencyResult>(DependencyResult.class))
                .fromCharSequence(new DependencyResultSpecNotationConverter())
                .toComposite();
    }
}
