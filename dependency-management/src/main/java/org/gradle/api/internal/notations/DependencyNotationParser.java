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
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.dependencies.DependencyVariant;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleFactoryHelper;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarFactory;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeConversionException;

public class DependencyNotationParser {
    public static NotationParser<Object, Dependency> parser(Instantiator instantiator,
                                                            DefaultProjectDependencyFactory dependencyFactory,
                                                            ClassPathRegistry classPathRegistry,
                                                            FileCollectionFactory fileCollectionFactory,
                                                            RuntimeShadedJarFactory runtimeShadedJarFactory,
                                                            CurrentGradleInstallation currentGradleInstallation,
                                                            Interner<String> stringInterner,
                                                            ImmutableAttributesFactory attributesFactory,
                                                            NotationParser<Object, Capability> capabilityNotationParser) {
        return NotationParserBuilder
            .toType(Dependency.class)
            .fromCharSequence(new DependencyStringNotationConverter<>(instantiator, DefaultExternalModuleDependency.class, stringInterner))
            .fromType(MinimalExternalModuleDependency.class, new MinimalExternalDependencyNotationConverter(instantiator, attributesFactory, capabilityNotationParser))
            .converter(new DependencyMapNotationConverter<>(instantiator, DefaultExternalModuleDependency.class))
            .fromType(FileCollection.class, new DependencyFilesNotationConverter(instantiator))
            .fromType(Project.class, new DependencyProjectNotationConverter(dependencyFactory))
            .fromType(DependencyFactory.ClassPathNotation.class, new DependencyClassPathNotationConverter(instantiator, classPathRegistry, fileCollectionFactory, runtimeShadedJarFactory, currentGradleInstallation))
            .invalidNotationMessage("Comprehensive documentation on dependency notations is available in DSL reference for DependencyHandler type.")
            .toComposite();
    }

    private static class MinimalExternalDependencyNotationConverter implements NotationConverter<MinimalExternalModuleDependency, DefaultExternalModuleDependency> {
        private final Instantiator instantiator;
        private final ImmutableAttributesFactory attributesFactory;
        private final NotationParser<Object, Capability> capabilityNotationParser;

        public MinimalExternalDependencyNotationConverter(Instantiator instantiator, ImmutableAttributesFactory attributesFactory, NotationParser<Object, Capability> capabilityNotationParser) {
            this.instantiator = instantiator;
            this.attributesFactory = attributesFactory;
            this.capabilityNotationParser = capabilityNotationParser;
        }

        @Override
        public void convert(MinimalExternalModuleDependency notation, NotationConvertResult<? super DefaultExternalModuleDependency> result) throws TypeConversionException {
            DefaultExternalModuleDependency moduleDependency = instantiator.newInstance(DefaultExternalModuleDependency.class, notation.getModule(), notation.getVersionConstraint());
            if (notation instanceof DependencyVariant) {
                moduleDependency.setAttributesFactory(attributesFactory);
                moduleDependency.setCapabilityNotationParser(capabilityNotationParser);
                DependencyVariant dependencyVariant = (DependencyVariant) notation;
                moduleDependency.attributes(dependencyVariant::mutateAttributes);
                moduleDependency.capabilities(dependencyVariant::mutateCapabilities);
                String classifier = dependencyVariant.getClassifier();
                String artifactType = dependencyVariant.getArtifactType();
                if (classifier != null || artifactType != null) {
                    ModuleFactoryHelper.addExplicitArtifactsIfDefined(moduleDependency, artifactType, classifier);
                }
            }
            result.converted(moduleDependency);
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
        }


    }
}
