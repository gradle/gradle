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

package org.gradle.api.internal.artifacts;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.InputArtifactDependencies;
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyContextManager;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.ModuleSelectorStringNotationConverter;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultDependencyMetadataFactory;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultExcludeRuleConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultLocalConfigurationMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyMetadataFactory;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ExcludeRuleConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ExternalModuleDependencyMetadataConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ProjectDependencyMetadataConverter;
import org.gradle.api.internal.artifacts.transform.CacheableTransformTypeAnnotationHandler;
import org.gradle.api.internal.artifacts.transform.InputArtifactAnnotationHandler;
import org.gradle.api.internal.artifacts.transform.InputArtifactDependenciesAnnotationHandler;
import org.gradle.api.internal.artifacts.transform.TransformActionScheme;
import org.gradle.api.internal.artifacts.transform.TransformParameterScheme;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.api.internal.tasks.properties.InspectionSchemeFactory;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.internal.component.external.model.PreferJavaRuntimeVariant;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.properties.annotations.TypeAnnotationHandler;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.connector.ResourceConnectorFactory;
import org.gradle.internal.resource.transport.file.FileConnectorFactory;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.typeconversion.CrossBuildCachingNotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.work.Incremental;
import org.gradle.work.NormalizeLineEndings;

class DependencyManagementGlobalScopeServices implements ServiceRegistrationProvider {
    void configure(ServiceRegistration registration) {
        registration.add(VersionParser.class);
        registration.add(IvyContextManager.class, DefaultIvyContextManager.class);
        registration.add(ImmutableModuleIdentifierFactory.class, DefaultImmutableModuleIdentifierFactory.class);
        registration.add(ExcludeRuleConverter.class, DefaultExcludeRuleConverter.class);
        registration.add(LocalConfigurationMetadataBuilder.class, DefaultLocalConfigurationMetadataBuilder.class);
    }

    @Provides
    NotationParser<Object, ComponentSelector> createComponentSelectorFactory(ImmutableModuleIdentifierFactory moduleIdentifierFactory, CrossBuildInMemoryCacheFactory cacheFactory) {
        return NotationParserBuilder
            .toType(ComponentSelector.class)
            .converter(new CrossBuildCachingNotationConverter<>(new ModuleSelectorStringNotationConverter(moduleIdentifierFactory), cacheFactory.newCache()))
            .toComposite();
    }

    @Provides
    DependencyMetadataFactory createDependencyMetadataFactory(ExcludeRuleConverter excludeRuleConverter) {
        return new DefaultDependencyMetadataFactory(
            new ProjectDependencyMetadataConverter(excludeRuleConverter),
            new ExternalModuleDependencyMetadataConverter(excludeRuleConverter)
        );
    }

    @Provides
    ResourceConnectorFactory createFileConnectorFactory() {
        return new FileConnectorFactory();
    }

    @Provides
    ProducerGuard<ExternalResourceName> createProducerAccess() {
        return ProducerGuard.adaptive();
    }

    @Provides
    TypeAnnotationHandler createCacheableTransformAnnotationHandler() {
        return new CacheableTransformTypeAnnotationHandler();
    }

    @Provides
    InputArtifactAnnotationHandler createInputArtifactAnnotationHandler() {
        return new InputArtifactAnnotationHandler();
    }

    @Provides
    InputArtifactDependenciesAnnotationHandler createInputArtifactDependenciesAnnotationHandler() {
        return new InputArtifactDependenciesAnnotationHandler();
    }

    @Provides
    PreferJavaRuntimeVariant createPreferJavaRuntimeVariant(NamedObjectInstantiator instantiator) {
        return new PreferJavaRuntimeVariant(instantiator);
    }

    @Provides
    PlatformSupport createPlatformSupport(NamedObjectInstantiator instantiator) {
        return new PlatformSupport(instantiator);
    }

    @Provides
    TransformParameterScheme createTransformParameterScheme(InspectionSchemeFactory inspectionSchemeFactory, InstantiatorFactory instantiatorFactory) {
        InstantiationScheme instantiationScheme = instantiatorFactory.decorateScheme();
        InspectionScheme inspectionScheme = inspectionSchemeFactory.inspectionScheme(
            ImmutableSet.of(
                Console.class,
                Input.class,
                InputDirectory.class,
                InputFile.class,
                InputFiles.class,
                Internal.class,
                Nested.class,
                ReplacedBy.class,
                ServiceReference.class
            ),
            ImmutableSet.of(
                Classpath.class,
                CompileClasspath.class,
                Incremental.class,
                Optional.class,
                PathSensitive.class,
                IgnoreEmptyDirectories.class,
                NormalizeLineEndings.class
            ),
            instantiationScheme
        );
        return new TransformParameterScheme(instantiationScheme, inspectionScheme);
    }

    @Provides
    TransformActionScheme createTransformActionScheme(InspectionSchemeFactory inspectionSchemeFactory, InstantiatorFactory instantiatorFactory) {
        InstantiationScheme instantiationScheme = instantiatorFactory.injectScheme(ImmutableSet.of(
            InputArtifact.class,
            InputArtifactDependencies.class
        ));
        InspectionScheme inspectionScheme = inspectionSchemeFactory.inspectionScheme(
            ImmutableSet.of(
                InputArtifact.class,
                InputArtifactDependencies.class
            ),
            ImmutableSet.of(
                Classpath.class,
                CompileClasspath.class,
                Incremental.class,
                Optional.class,
                PathSensitive.class,
                IgnoreEmptyDirectories.class,
                NormalizeLineEndings.class
            ),
            instantiationScheme
        );
        return new TransformActionScheme(instantiationScheme, inspectionScheme);
    }
}
