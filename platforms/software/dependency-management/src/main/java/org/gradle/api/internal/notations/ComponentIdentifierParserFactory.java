/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.internal.Factory;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.typeconversion.MapKey;
import org.gradle.internal.typeconversion.MapNotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypedNotationConverter;

import javax.annotation.Nullable;

import static org.gradle.api.internal.notations.ModuleNotationValidation.validate;

public class ComponentIdentifierParserFactory implements Factory<NotationParser<Object, ComponentIdentifier>> {

    @Nullable
    @Override
    public NotationParser<Object, ComponentIdentifier> create() {
        return NotationParserBuilder.toType(ComponentIdentifier.class)
            .fromCharSequence(new StringNotationConverter())
            .converter(new ComponentIdentifierMapNotationConverter())
            .toComposite();
    }

    static class ComponentIdentifierMapNotationConverter extends MapNotationConverter<ModuleComponentIdentifier> {
        protected ModuleComponentIdentifier parseMap(
            @MapKey("group") String group,
            @MapKey("name") String name,
            @MapKey("version") String version) {

            return DefaultModuleComponentIdentifier.newId(
                DefaultModuleIdentifier.newId(validate(group.trim()), validate(name.trim())),
                validate(version.trim())
            );
        }
    }

    static class StringNotationConverter extends TypedNotationConverter<String, ModuleComponentIdentifier> {

        StringNotationConverter() {
            super(String.class);
        }

        @Override
        protected ModuleComponentIdentifier parseType(String notation) {
            String[] parts = notation.split(":");
            if (parts.length != 3) {
                throw new InvalidUserDataException("Invalid module component notation: " + notation + " : must be a valid 3 part identifier, eg.: org.gradle:gradle:1.0");
            }
            return DefaultModuleComponentIdentifier.newId(
                DefaultModuleIdentifier.newId(validate(parts[0].trim(), notation), validate(parts[1].trim(), notation)),
                validate(parts[2].trim(), notation)
            );
        }
    }
}
