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

package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.MapKey;
import org.gradle.internal.typeconversion.MapNotationConverter;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeConversionException;
import org.gradle.internal.typeconversion.TypeInfo;
import org.gradle.internal.typeconversion.TypedNotationConverter;

import java.util.Set;

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector;

public class ModuleVersionSelectorParsers {

    public static NotationParser<Object, Set<ModuleVersionSelector>> multiParser(String dslContext) {
        return builder(dslContext).toFlatteningComposite();
    }

    public static NotationParser<Object, ModuleVersionSelector> parser(String dslContext) {
        return builder(dslContext).toComposite();
    }

    private static NotationParserBuilder<Object, ModuleVersionSelector> builder(String dslContext) {
        return NotationParserBuilder
            .toType(ModuleVersionSelector.class)
            .fromCharSequence(new StringConverter())
            .converter(new MapConverter())
            .converter(new ProviderConverter(dslContext))
            .converter(new ProviderConvertibleConverter(dslContext));
    }

    static class MapConverter extends MapNotationConverter<ModuleVersionSelector> {
        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Maps").example("[group: 'org.gradle', name:'gradle-core', version: '1.0']");
        }

        protected ModuleVersionSelector parseMap(@MapKey("group") String group, @MapKey("name") String name, @MapKey("version") String version) {
            return newSelector(DefaultModuleIdentifier.newId(group, name), version);
        }
    }

    static class StringConverter implements NotationConverter<String, ModuleVersionSelector> {
        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("String or CharSequence values").example("'org.gradle:gradle-core:1.0'");
        }

        @Override
        public void convert(String notation, NotationConvertResult<? super ModuleVersionSelector> result) throws TypeConversionException {
            ParsedModuleStringNotation parsed;
            try {
                parsed = new ParsedModuleStringNotation(notation, null);
            } catch (IllegalDependencyNotation e) {
                throw new InvalidUserDataException(
                        "Invalid format: '" + notation + "'. The correct notation is a 3-part group:name:version notation, "
                                + "e.g: 'org.gradle:gradle-core:1.0'");
            }

            if (parsed.getGroup() == null || parsed.getName() == null || parsed.getVersion() == null) {
                throw new InvalidUserDataException(
                        "Invalid format: '" + notation + "'. Group, name and version cannot be empty. Correct example: "
                                + "'org.gradle:gradle-core:1.0'");
            }
            result.converted(newSelector(DefaultModuleIdentifier.newId(parsed.getGroup(), parsed.getName()), parsed.getVersion()));
        }
    }

    static class ProviderConvertibleConverter extends TypedNotationConverter<ProviderConvertible<?>, ModuleVersionSelector> {

        private final ProviderConverter providerConverter;

        public ProviderConvertibleConverter(String caller) {
            super(new TypeInfo<>(ProviderConvertible.class));
            this.providerConverter = new ProviderConverter(caller);
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Version catalog type-safe accessors.");
        }

        @Override
        protected ModuleVersionSelector parseType(ProviderConvertible<?> notation) {
            return providerConverter.parseType(notation.asProvider());
        }

    }

    static class ProviderConverter extends TypedNotationConverter<Provider<?>, ModuleVersionSelector> {

        private final String caller;

        public ProviderConverter(String caller) {
            super(new TypeInfo<>(Provider.class));
            this.caller = caller;
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Version catalog type-safe accessors.");
        }

        @Override
        protected ModuleVersionSelector parseType(Provider<?> notation) {
            Class<?> providerTargetClass = getProviderTargetClass(notation);
            if (!MinimalExternalModuleDependency.class.isAssignableFrom(providerTargetClass)) {
                String notationAsString = notation.getOrNull() == null ? null : notation.get().toString();
                throw new InvalidUserDataException("Cannot convert a version catalog entry '" + notationAsString + "' to an object of type ModuleVersionSelector. " +
                    "Only dependency accessors are supported but not plugin, bundle or version accessors for '" + caller + "'.");
            }
            MinimalExternalModuleDependency dependency = (MinimalExternalModuleDependency) notation.get();
            if (isNotRequiredVersionOnly(dependency.getVersionConstraint())) {
                throw new InvalidUserDataException("Cannot convert a version catalog entry: '" + notation.get() + "' to an object of type ModuleVersionSelector. Rich versions are not supported for '" + caller + "'.");
            } else if (dependency.getVersionConstraint().getRequiredVersion().isEmpty()) {
                throw new InvalidUserDataException("Cannot convert a version catalog entry: '" + notation.get() + "' to an object of type ModuleVersionSelector. Version cannot be empty for '" + caller + "'.");
            } else {
                return newSelector(dependency.getModule(), dependency.getVersionConstraint().getRequiredVersion());
            }
        }

        @SuppressWarnings("ConstantConditions")
        private Class<?> getProviderTargetClass(Provider<?> notation) {
            return notation.getOrNull() == null
                ? null
                : notation.get().getClass();
        }

        private boolean isNotRequiredVersionOnly(VersionConstraint constraint) {
            return !constraint.getPreferredVersion().isEmpty()
                || !constraint.getStrictVersion().isEmpty()
                || !constraint.getRejectedVersions().isEmpty()
                || constraint.getBranch() != null;
        }

    }
}
