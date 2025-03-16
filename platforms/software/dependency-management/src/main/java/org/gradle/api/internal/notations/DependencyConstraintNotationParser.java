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
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.provider.Provider;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.NotationConverterToNotationParserAdapter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeConversionException;
import org.gradle.internal.typeconversion.TypedNotationConverter;

public class DependencyConstraintNotationParser {

    public static DependencyConstraintNotationParser parser(Instantiator instantiator, DefaultProjectDependencyFactory dependencyFactory, Interner<String> stringInterner, AttributesFactory attributesFactory) {
        DependencyStringNotationConverter<DefaultDependencyConstraint> stringNotationConverter = new DependencyStringNotationConverter<>(instantiator, DefaultDependencyConstraint.class, stringInterner);
        MinimalExternalDependencyNotationConverter minimalExternalDependencyNotationConverter = new MinimalExternalDependencyNotationConverter(instantiator, attributesFactory);
        ProjectDependencyNotationConverter projectDependencyNotationConverter = new ProjectDependencyNotationConverter(instantiator);
        NotationParser<Object, DependencyConstraint> notationParser = NotationParserBuilder
            .toType(DependencyConstraint.class)
            .fromType(MinimalExternalModuleDependency.class, minimalExternalDependencyNotationConverter)
            .fromCharSequence(stringNotationConverter)
            .converter(new DependencyMapNotationConverter<>(instantiator, DefaultDependencyConstraint.class))
            .fromType(Project.class, new DependencyConstraintProjectNotationConverter(instantiator, dependencyFactory))
            .converter(projectDependencyNotationConverter)
            .invalidNotationMessage("Comprehensive documentation on dependency notations is available in DSL reference for DependencyConstraintHandler type.")
            .toComposite();
        return new DependencyConstraintNotationParser(
            notationParser,
            new NotationConverterToNotationParserAdapter<>(stringNotationConverter),
            new NotationConverterToNotationParserAdapter<>(minimalExternalDependencyNotationConverter),
            new NotationConverterToNotationParserAdapter<>(projectDependencyNotationConverter)
        );
    }

    private final NotationParser<Object, DependencyConstraint> notationParser;
    private final NotationParser<String, ? extends DependencyConstraint> stringNotationParser;
    private final NotationParser<MinimalExternalModuleDependency, ? extends DependencyConstraint> minimalExternalModuleDependencyNotationParser;
    private final NotationParser<ProjectDependency, ? extends DependencyConstraint> projectDependencyNotationParser;

    private DependencyConstraintNotationParser(
        NotationParser<Object, DependencyConstraint> notationParser,
        NotationParser<String, ? extends DependencyConstraint> stringNotationParser,
        NotationParser<MinimalExternalModuleDependency, ? extends DependencyConstraint> minimalExternalModuleDependencyNotationParser,
        NotationParser<ProjectDependency, ? extends DependencyConstraint> projectDependencyNotationParser
    ) {
        this.notationParser = notationParser;
        this.stringNotationParser = stringNotationParser;
        this.minimalExternalModuleDependencyNotationParser = minimalExternalModuleDependencyNotationParser;
        this.projectDependencyNotationParser = projectDependencyNotationParser;
    }

    public NotationParser<Object, DependencyConstraint> getNotationParser() {
        return notationParser;
    }

    public NotationParser<String, ? extends DependencyConstraint> getStringNotationParser() {
        return stringNotationParser;
    }

    public NotationParser<MinimalExternalModuleDependency, ? extends DependencyConstraint> getMinimalExternalModuleDependencyNotationParser() {
        return minimalExternalModuleDependencyNotationParser;
    }

    public NotationParser<ProjectDependency, ? extends DependencyConstraint> getProjectDependencyNotationParser() {
        return projectDependencyNotationParser;
    }

    private static class ProjectDependencyNotationConverter extends TypedNotationConverter<ProjectDependency, DependencyConstraint> {
        private final Instantiator instantiator;

        public ProjectDependencyNotationConverter(Instantiator instantiator) {
            super(ProjectDependency.class);
            this.instantiator = instantiator;
        }

        @Override
        protected DependencyConstraint parseType(ProjectDependency notation) {
            return instantiator.newInstance(DefaultProjectDependencyConstraint.class, notation);
        }
    }

    private static class MinimalExternalDependencyNotationConverter implements NotationConverter<MinimalExternalModuleDependency, DefaultDependencyConstraint> {
        private final Instantiator instantiator;
        private final AttributesFactory attributesFactory;

        public MinimalExternalDependencyNotationConverter(Instantiator instantiator, AttributesFactory attributesFactory) {
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

        @Override
        public void requireFeature(String featureName) {
            throw new InvalidUserDataException("Capabilities are not supported by dependency constraints");
        }

        @Override
        public void requireFeature(Provider<String> featureName) {
            throw new InvalidUserDataException("Capabilities are not supported by dependency constraints");
        }
    }
}
