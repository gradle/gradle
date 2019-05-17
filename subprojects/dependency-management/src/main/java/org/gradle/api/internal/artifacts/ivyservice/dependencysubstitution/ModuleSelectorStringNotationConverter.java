/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution;

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.TypedNotationConverter;
import org.gradle.internal.typeconversion.UnsupportedNotationException;
import org.gradle.util.GUtil;

import static org.gradle.api.internal.notations.ModuleNotationValidation.*;

class ModuleSelectorStringNotationConverter extends TypedNotationConverter<String, ComponentSelector> {
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

    public ModuleSelectorStringNotationConverter(ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        super(String.class);
        this.moduleIdentifierFactory = moduleIdentifierFactory;
    }

    /**
     * Empty String for either group or module name is not allowed.
     */
    @Override
    protected ComponentSelector parseType(String notation) {
        assert notation != null;
        String[] split = notation.split(":");

        if (split.length < 2 || split.length > 3) {
            throw new UnsupportedNotationException(notation);
        }
        String group = validate(split[0].trim(), notation);
        String name = validate(split[1].trim(), notation);

        if (split.length == 2) {
            return new UnversionedModuleComponentSelector(moduleIdentifierFactory.module(group, name));
        }
        String version = split[2].trim();
        if (!GUtil.isTrue(version)) {
            throw new UnsupportedNotationException(notation);
        }
        return DefaultModuleComponentSelector.newSelector(moduleIdentifierFactory.module(group, name), DefaultImmutableVersionConstraint.of(version));
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("String describing the module in 'group:name' format").example("'org.gradle:gradle-core'.");
        visitor.candidate("String describing the selector in 'group:name:version' format").example("'org.gradle:gradle-core:1.+'.");
    }
}
