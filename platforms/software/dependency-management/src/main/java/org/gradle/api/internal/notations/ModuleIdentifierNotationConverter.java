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

package org.gradle.api.internal.notations;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.TypeConversionException;
import org.gradle.internal.typeconversion.UnsupportedNotationException;

import static org.gradle.api.internal.notations.ModuleNotationValidation.validate;

public class ModuleIdentifierNotationConverter implements NotationConverter<String, ModuleIdentifier> {

    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

    public ModuleIdentifierNotationConverter(ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this.moduleIdentifierFactory = moduleIdentifierFactory;
    }

    /**
     * Empty String for either group or module name is not allowed.
     */
    @Override
    public void convert(String notation, NotationConvertResult<? super ModuleIdentifier> result) throws TypeConversionException {
        assert notation != null;
        String[] split = notation.split(":");
        if (split.length != 2) {
            throw new UnsupportedNotationException(notation);
        }
        String group = validate(split[0].trim(), notation);
        String name = validate(split[1].trim(), notation);
        result.converted(moduleIdentifierFactory.module(group, name));
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("String describing the module in 'group:name' format").example("'org.gradle:gradle-core'");
    }
}
