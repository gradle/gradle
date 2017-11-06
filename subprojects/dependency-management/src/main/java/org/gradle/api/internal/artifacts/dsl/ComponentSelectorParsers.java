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

package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.MapKey;
import org.gradle.internal.typeconversion.MapNotationConverter;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeConversionException;

import java.util.Set;

import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector;

public class ComponentSelectorParsers {

    private static final NotationParserBuilder<ComponentSelector> BUILDER = NotationParserBuilder
            .toType(ComponentSelector.class)
            .fromCharSequence(new StringConverter())
            .converter(new MapConverter())
            .fromType(Project.class, new ProjectConverter());

    public static NotationParser<Object, Set<ComponentSelector>> multiParser() {
        return builder().toFlatteningComposite();
    }

    public static NotationParser<Object, ComponentSelector> parser() {
        return builder().toComposite();
    }

    private static NotationParserBuilder<ComponentSelector> builder() {
        return BUILDER;
    }

    static class MapConverter extends MapNotationConverter<ComponentSelector> {
        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.example("Maps, e.g. [group: 'org.gradle', name:'gradle-core', version: '1.0'].");
        }

        protected ModuleComponentSelector parseMap(@MapKey("group") String group, @MapKey("name") String name, @MapKey("version") String version) {
            return newSelector(group, name, DefaultImmutableVersionConstraint.of(version));
        }
    }

    static class StringConverter implements NotationConverter<String, ComponentSelector> {
        public void describe(DiagnosticsVisitor visitor) {
            visitor.example("String or CharSequence values, e.g. 'org.gradle:gradle-core:1.0'.");
        }

        public void convert(String notation, NotationConvertResult<? super ComponentSelector> result) throws TypeConversionException {
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
            result.converted(newSelector(parsed.getGroup(), parsed.getName(), DefaultImmutableVersionConstraint.of(parsed.getVersion())));
        }
    }

    static class ProjectConverter implements NotationConverter<Project, ComponentSelector> {

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.example("Project objects, e.g. project(':api').");
        }

        @Override
        public void convert(Project notation, NotationConvertResult<? super ComponentSelector> result) throws TypeConversionException {
            result.converted(DefaultProjectComponentSelector.newSelector(notation));
        }
    }
}
