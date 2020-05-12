/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypedNotationConverter;

public class DependencyConstraintNotationParser {
    public static NotationParser<Object, DependencyConstraint> parser(Instantiator instantiator, DefaultProjectDependencyFactory dependencyFactory, Interner<String> stringInterner) {
        return NotationParserBuilder
            .toType(DependencyConstraint.class)
            .fromCharSequence(new DependencyStringNotationConverter<>(instantiator, DefaultDependencyConstraint.class, stringInterner))
            .converter(new DependencyMapNotationConverter<>(instantiator, DefaultDependencyConstraint.class))
            .fromType(Project.class, new DependencyConstraintProjectNotationConverter(dependencyFactory))
            .converter(new ProjectDependencyNotationConverter())
            .invalidNotationMessage("Comprehensive documentation on dependency notations is available in DSL reference for DependencyHandler type.")
            .toComposite();
    }

    private static class ProjectDependencyNotationConverter extends TypedNotationConverter<ProjectDependency, DependencyConstraint> {

        public ProjectDependencyNotationConverter() {
            super(ProjectDependency.class);
        }

        @Override
        protected DependencyConstraint parseType(ProjectDependency notation) {
            return new DefaultProjectDependencyConstraint(notation);
        }
    }
}
