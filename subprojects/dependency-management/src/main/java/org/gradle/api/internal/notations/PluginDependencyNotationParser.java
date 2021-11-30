/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.collect.Interner;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultPluginDependency;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.MapKey;
import org.gradle.internal.typeconversion.MapNotationConverter;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeConversionException;
import org.gradle.plugin.use.PluginDependency;

import javax.annotation.Nullable;

import static org.gradle.api.internal.notations.ModuleNotationValidation.validate;

public class PluginDependencyNotationParser {
    public static NotationParser<Object, PluginDependency> parser(
        Interner<String> stringInterner
    ) {
        return NotationParserBuilder
            .toType(PluginDependency.class)
            .fromCharSequence(new StringConverter(stringInterner))
            .converter(new MapConverter(stringInterner))
            .invalidNotationMessage("Comprehensive documentation on dependency notations is available in DSL reference for DependencyHandler type.")
            .toComposite();
    }

    private static class StringConverter implements NotationConverter<String, PluginDependency> {
        private final Interner<String> stringInterner;

        StringConverter(Interner<String> stringInterner) {
            this.stringInterner = stringInterner;
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("String or CharSequence values").example("'org.gradle.java:1.0'");
        }

        @Override
        public void convert(String notation, NotationConvertResult<? super PluginDependency> result) throws TypeConversionException {
            result.converted(createDependencyFromString(stringInterner, notation));
        }

        private PluginDependency createDependencyFromString(Interner<String> stringInterner, String notation) {
            String[] coordinates = notation.split(":");
            if (coordinates.length > 2) {
                throw new IllegalDependencyNotation("Supplied String plugin notation '" + notation + "' is invalid. Example notation: 'org.gradle.java:1.0'.");
            }
            return createDependency(stringInterner, coordinates[0], coordinates.length > 1 ? coordinates[1] : null);
        }

    }

    private static class MapConverter extends MapNotationConverter<PluginDependency> {
        private final Interner<String> stringInterner;

        MapConverter(Interner<String> stringInterner) {
            this.stringInterner = stringInterner;
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Maps").example("[id: 'org.gradle.java', version: '1.0']");
        }

        protected PluginDependency parseMap(
            @MapKey("id") String id,
            @MapKey("version") @Nullable String version
        ) {
            return createDependency(stringInterner, id, version);
        }

    }

    private static PluginDependency createDependency(
        Interner<String> stringInterner,
        String pluginId,
        @Nullable String version
    ) {
        return new DefaultPluginDependency(
            validate(stringInterner.intern(pluginId.trim())),
            DefaultMutableVersionConstraint.withVersion(version == null ? null : validate(stringInterner.intern(version.trim())))
        );
    }

}
