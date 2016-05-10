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

package org.gradle.api.internal.notations;

import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.internal.artifacts.dsl.ParsedModuleStringNotation;
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleFactoryHelper;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.TypeConversionException;

public class DependencyStringNotationConverter<T extends ExternalDependency> implements NotationConverter<String, T> {
    private final Instantiator instantiator;
    private final Class<T> wantedType;

    public DependencyStringNotationConverter(Instantiator instantiator, Class<T> wantedType) {
        this.instantiator = instantiator;
        this.wantedType = wantedType;
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("String or CharSequence values").example("'org.gradle:gradle-core:1.0'");
    }

    public void convert(String notation, NotationConvertResult<? super T> result) throws TypeConversionException {
        result.converted(createDependencyFromString(notation));
    }

    private T createDependencyFromString(String notation) {

        ParsedModuleStringNotation parsedNotation = splitModuleFromExtension(notation);
        T moduleDependency = instantiator.newInstance(wantedType,
            parsedNotation.getGroup(), parsedNotation.getName(), parsedNotation.getVersion());
        ModuleFactoryHelper.addExplicitArtifactsIfDefined(moduleDependency, parsedNotation.getArtifactType(), parsedNotation.getClassifier());

        return moduleDependency;
    }

    private ParsedModuleStringNotation splitModuleFromExtension(String notation) {
        int idx = notation.lastIndexOf('@');
        if (idx == -1 || ClientModule.class.isAssignableFrom(wantedType)) {
            return new ParsedModuleStringNotation(notation, null);
        }
        int versionIndx = notation.lastIndexOf(':');
        if (versionIndx<idx) {
            return new ParsedModuleStringNotation(notation.substring(0, idx), notation.substring(idx + 1));
        }
        return new ParsedModuleStringNotation(notation, null);
    }
}
