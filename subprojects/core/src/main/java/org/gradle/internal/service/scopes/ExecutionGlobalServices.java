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
package org.gradle.internal.service.scopes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import groovy.lang.GroovyObject;
import groovy.transform.Generated;
import org.gradle.api.Describable;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.InputArtifactDependencies;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.internal.project.taskfactory.DefaultTaskClassInfoStore;
import org.gradle.api.internal.project.taskfactory.TaskClassInfoStore;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.api.internal.tasks.properties.InspectionSchemeFactory;
import org.gradle.api.internal.tasks.properties.TaskScheme;
import org.gradle.api.internal.tasks.properties.annotations.CacheableTaskTypeAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.DestroysPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.LocalStatePropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OutputDirectoriesPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OutputDirectoryPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OutputFilePropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OutputFilesPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.UntrackedTaskTypeAnnotationHandler;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Destroys;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.LocalState;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.options.OptionValues;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.DefaultWorkExecutionTracker;
import org.gradle.internal.execution.WorkExecutionTracker;
import org.gradle.internal.execution.WorkInputListeners;
import org.gradle.internal.execution.history.ImmutableWorkspaceMetadataStore;
import org.gradle.internal.execution.history.impl.DefaultImmutableWorkspaceMetadataStore;
import org.gradle.internal.execution.model.annotations.DisableCachingByDefaultTypeAnnotationHandler;
import org.gradle.internal.execution.model.annotations.InputDirectoryPropertyAnnotationHandler;
import org.gradle.internal.execution.model.annotations.InputFilePropertyAnnotationHandler;
import org.gradle.internal.execution.model.annotations.InputFilesPropertyAnnotationHandler;
import org.gradle.internal.execution.model.annotations.InputPropertyAnnotationHandler;
import org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory;
import org.gradle.internal.execution.model.annotations.ServiceReferencePropertyAnnotationHandler;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.properties.annotations.NestedBeanAnnotationHandler;
import org.gradle.internal.properties.annotations.NoOpPropertyAnnotationHandler;
import org.gradle.internal.properties.annotations.PropertyAnnotationHandler;
import org.gradle.internal.properties.annotations.TypeAnnotationHandler;
import org.gradle.internal.properties.bean.PropertyWalker;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadataStore;
import org.gradle.internal.reflect.annotations.impl.DefaultTypeAnnotationMetadataStore;
import org.gradle.internal.scripts.ScriptOrigin;
import org.gradle.util.internal.ConfigureUtil;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.work.Incremental;
import org.gradle.work.NormalizeLineEndings;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;

@SuppressWarnings("unused")
public class ExecutionGlobalServices {
    @VisibleForTesting
    public static final ImmutableSet<Class<? extends Annotation>> PROPERTY_TYPE_ANNOTATIONS = ImmutableSet.of(
        Console.class,
        Destroys.class,
        Input.class,
        InputArtifact.class,
        InputArtifactDependencies.class,
        InputDirectory.class,
        InputFile.class,
        InputFiles.class,
        LocalState.class,
        Nested.class,
        OptionValues.class,
        OutputDirectories.class,
        OutputDirectory.class,
        OutputFile.class,
        OutputFiles.class,
        ServiceReference.class
    );

    @VisibleForTesting
    public static final ImmutableSet<Class<? extends Annotation>> IGNORED_METHOD_ANNOTATIONS = ImmutableSet.of(
        Internal.class,
        ReplacedBy.class
    );

    WorkExecutionTracker createWorkExecutionTracker(BuildOperationAncestryTracker ancestryTracker, BuildOperationListenerManager operationListenerManager) {
        return new DefaultWorkExecutionTracker(ancestryTracker, operationListenerManager);
    }

    AnnotationHandlerRegistar createAnnotationRegistry(List<AnnotationHandlerRegistration> registrations) {
        return builder -> registrations.forEach(registration -> builder.addAll(registration.getAnnotations()));
    }

    TypeAnnotationMetadataStore createAnnotationMetadataStore(CrossBuildInMemoryCacheFactory cacheFactory, AnnotationHandlerRegistar annotationRegistry) {
        ImmutableSet.Builder<Class<? extends Annotation>> builder = ImmutableSet.builder();
        builder.addAll(PROPERTY_TYPE_ANNOTATIONS);
        annotationRegistry.registerPropertyTypeAnnotations(builder);
        return new DefaultTypeAnnotationMetadataStore(
            ImmutableSet.of(
                CacheableTask.class,
                CacheableTransform.class,
                DisableCachingByDefault.class,
                UntrackedTask.class
            ),
            ModifierAnnotationCategory.asMap(builder.build()),
            ImmutableSet.of(
                "java",
                "groovy",
                "kotlin"
            ),
            ImmutableSet.of(
                // Used by a nested bean with action in a task, example:
                // `NestedInputIntegrationTest.implementation of nested closure in decorated bean is tracked`
                ConfigureUtil.WrappedConfigureAction.class,
                // DefaultTestTaskReports used by AbstractTestTask extends this class
                DefaultNamedDomainObjectSet.class,
                // Used in gradle-base so it can't have annotations anyway
                Describable.class
            ),
            ImmutableSet.of(
                GroovyObject.class,
                Object.class,
                ScriptOrigin.class
            ),
            ImmutableSet.of(
                ConfigurableFileCollection.class,
                Property.class
            ),
            IGNORED_METHOD_ANNOTATIONS,
            method -> method.isAnnotationPresent(Generated.class),
            cacheFactory);
    }

    InspectionSchemeFactory createInspectionSchemeFactory(
        List<TypeAnnotationHandler> typeHandlers,
        List<PropertyAnnotationHandler> propertyHandlers,
        TypeAnnotationMetadataStore typeAnnotationMetadataStore,
        CrossBuildInMemoryCacheFactory cacheFactory
    ) {
        return new InspectionSchemeFactory(typeHandlers, propertyHandlers, typeAnnotationMetadataStore, cacheFactory);
    }

    TaskScheme createTaskScheme(InspectionSchemeFactory inspectionSchemeFactory, InstantiatorFactory instantiatorFactory, AnnotationHandlerRegistar annotationRegistry) {
        InstantiationScheme instantiationScheme = instantiatorFactory.decorateScheme();
        ImmutableSet.Builder<Class<? extends Annotation>> allPropertyTypes = ImmutableSet.builder();
        allPropertyTypes.addAll(ImmutableSet.of(
            Input.class,
            InputFile.class,
            InputFiles.class,
            InputDirectory.class,
            OutputFile.class,
            OutputFiles.class,
            OutputDirectory.class,
            OutputDirectories.class,
            Destroys.class,
            LocalState.class,
            Nested.class,
            Console.class,
            ReplacedBy.class,
            Internal.class,
            ServiceReference.class,
            OptionValues.class
        ));
        annotationRegistry.registerPropertyTypeAnnotations(allPropertyTypes);
        InspectionScheme inspectionScheme = inspectionSchemeFactory.inspectionScheme(
            allPropertyTypes.build(),
            ImmutableSet.of(
                Classpath.class,
                CompileClasspath.class,
                Incremental.class,
                Optional.class,
                PathSensitive.class,
                SkipWhenEmpty.class,
                IgnoreEmptyDirectories.class,
                NormalizeLineEndings.class
            ),
            instantiationScheme);
        return new TaskScheme(instantiationScheme, inspectionScheme);
    }

    PropertyWalker createPropertyWalker(TaskScheme taskScheme) {
        return taskScheme.getInspectionScheme().getPropertyWalker();
    }

    TaskClassInfoStore createTaskClassInfoStore(CrossBuildInMemoryCacheFactory cacheFactory) {
        return new DefaultTaskClassInfoStore(cacheFactory);
    }

    TypeAnnotationHandler createDoNotCacheByDefaultTypeAnnotationHandler() {
        return new DisableCachingByDefaultTypeAnnotationHandler();
    }

    TypeAnnotationHandler createCacheableTaskAnnotationHandler() {
        return new CacheableTaskTypeAnnotationHandler();
    }

    TypeAnnotationHandler createUntrackedAnnotationHandler() {
        return new UntrackedTaskTypeAnnotationHandler();
    }

    PropertyAnnotationHandler createConsoleAnnotationHandler() {
        return new NoOpPropertyAnnotationHandler(Console.class);
    }

    PropertyAnnotationHandler createInternalAnnotationHandler() {
        return new NoOpPropertyAnnotationHandler(Internal.class);
    }

    PropertyAnnotationHandler createServiceReferenceAnnotationHandler() {
        return new ServiceReferencePropertyAnnotationHandler();
    }

    PropertyAnnotationHandler createReplacedByAnnotationHandler() {
        return new NoOpPropertyAnnotationHandler(ReplacedBy.class);
    }

    PropertyAnnotationHandler createOptionValuesAnnotationHandler() {
        return new NoOpPropertyAnnotationHandler(OptionValues.class);
    }

    PropertyAnnotationHandler createInputPropertyAnnotationHandler() {
        return new InputPropertyAnnotationHandler();
    }

    PropertyAnnotationHandler createInputFilePropertyAnnotationHandler() {
        return new InputFilePropertyAnnotationHandler();
    }

    PropertyAnnotationHandler createInputFilesPropertyAnnotationHandler() {
        return new InputFilesPropertyAnnotationHandler();
    }

    PropertyAnnotationHandler createInputDirectoryPropertyAnnotationHandler() {
        return new InputDirectoryPropertyAnnotationHandler();
    }

    OutputFilePropertyAnnotationHandler createOutputFilePropertyAnnotationHandler() {
        return new OutputFilePropertyAnnotationHandler();
    }

    OutputFilesPropertyAnnotationHandler createOutputFilesPropertyAnnotationHandler() {
        return new OutputFilesPropertyAnnotationHandler();
    }

    OutputDirectoryPropertyAnnotationHandler createOutputDirectoryPropertyAnnotationHandler() {
        return new OutputDirectoryPropertyAnnotationHandler();
    }

    OutputDirectoriesPropertyAnnotationHandler createOutputDirectoriesPropertyAnnotationHandler() {
        return new OutputDirectoriesPropertyAnnotationHandler();
    }

    PropertyAnnotationHandler createDestroysPropertyAnnotationHandler() {
        return new DestroysPropertyAnnotationHandler();
    }

    PropertyAnnotationHandler createLocalStatePropertyAnnotationHandler() {
        return new LocalStatePropertyAnnotationHandler();
    }

    PropertyAnnotationHandler createNestedBeanPropertyAnnotationHandler() {
        return new NestedBeanAnnotationHandler(ImmutableSet.of(Optional.class));
    }

    WorkInputListeners createWorkInputListeners(ListenerManager listenerManager) {
        return new DefaultWorkInputListeners(listenerManager);
    }

    public interface AnnotationHandlerRegistration {
        Collection<Class<? extends Annotation>> getAnnotations();
    }

    interface AnnotationHandlerRegistar {
        void registerPropertyTypeAnnotations(ImmutableSet.Builder<Class<? extends Annotation>> builder);
    }

    ImmutableWorkspaceMetadataStore createImmutableWorkspaceMetadataStore() {
        return new DefaultImmutableWorkspaceMetadataStore();
    }
}
