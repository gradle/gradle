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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.api.internal.artifacts.dependencies.DependencyVariant;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeConversionException;
import org.gradle.internal.typeconversion.TypedNotationConverter;

public class DependencyConstraintNotationParser {
    public static NotationParser<Object, DependencyConstraint> parser(Instantiator instantiator, DefaultProjectDependencyFactory dependencyFactory, Interner<String> stringInterner, ImmutableAttributesFactory attributesFactory) {
        return NotationParserBuilder
            .toType(DependencyConstraint.class)
            .fromType(MinimalExternalModuleDependency.class, new MinimalExternalDependencyNotationConverter(instantiator, attributesFactory))
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

    private static class MinimalExternalDependencyNotationConverter implements NotationConverter<MinimalExternalModuleDependency, DefaultDependencyConstraint> {
        private final Instantiator instantiator;
        private final ImmutableAttributesFactory attributesFactory;

        public MinimalExternalDependencyNotationConverter(Instantiator instantiator, ImmutableAttributesFactory attributesFactory) {
            this.instantiator = instantiator;
            this.attributesFactory = attributesFactory;
        }

        @Override
        public void convert(MinimalExternalModuleDependency notation, NotationConvertResult<? super DefaultDependencyConstraint> result) throws TypeConversionException {
            DefaultDependencyConstraint dependencyConstraint = instantiator.newInstance(DefaultDependencyConstraint.class, notation.getModule(), notation.getVersionConstraint());
            if (notation instanceof DependencyVariant) {
                dependencyConstraint.setAttributesFactory(attributesFactory);
                DependencyVariant dependencyVariant = (DependencyVariant) notation;
                dependencyConstraint.attributes(dependencyVariant::mutateAttributes);
                dependencyVariant.mutateCapabilities(UnsupportedCapabilitiesHandler.INSTANCE);
                String classifier = dependencyVariant.getClassifier();
                String artifactType = dependencyVariant.getArtifactType();
                if (classifier != null || artifactType != null) {
                    throw new InvalidUserDataException("Classifier and artifact types aren't supported by dependency constraints");
                }
            }
            result.converted(dependencyConstraint);
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
        }
    }

    private final static class UnsupportedCapabilitiesHandler implements ModuleDependencyCapabilitiesHandler {
        private final static UnsupportedCapabilitiesHandler INSTANCE = new UnsupportedCapabilitiesHandler();

        @Override
        public void requireCapability(Object capabilityNotation) {
            throw new InvalidUserDataException("Capabilities are not supported by dependency constraints");
        }

        @Override
        public void requireCapabilities(Object... capabilityNotations) {
            throw new InvalidUserDataException("Capabilities are not supported by dependency constraints");
        }
    }
}
