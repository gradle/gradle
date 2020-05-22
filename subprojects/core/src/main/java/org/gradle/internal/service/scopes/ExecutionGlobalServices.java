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
import org.gradle.api.DefaultTask;
import org.gradle.api.Describable;
import org.gradle.api.Task;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.InputArtifactDependencies;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.DefaultDomainObjectCollection;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.DefaultNamedDomainObjectCollection;
import org.gradle.api.internal.DefaultNamedDomainObjectList;
import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.project.taskfactory.DefaultTaskClassInfoStore;
import org.gradle.api.internal.project.taskfactory.TaskClassInfoStore;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.api.internal.tasks.properties.InspectionSchemeFactory;
import org.gradle.api.internal.tasks.properties.ModifierAnnotationCategory;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.internal.tasks.properties.TaskScheme;
import org.gradle.api.internal.tasks.properties.annotations.CacheableTaskTypeAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.DestroysPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.InputDirectoryPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.InputFilePropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.InputFilesPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.InputPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.LocalStatePropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.NestedBeanAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.NoOpPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OutputDirectoriesPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OutputDirectoryPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OutputFilePropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OutputFilesPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.TypeAnnotationHandler;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Destroys;
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
import org.gradle.api.tasks.options.OptionValues;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadataStore;
import org.gradle.internal.reflect.annotations.impl.DefaultTypeAnnotationMetadataStore;
import org.gradle.internal.scripts.ScriptOrigin;
import org.gradle.util.ClosureBackedAction;
import org.gradle.util.ConfigureUtil;
import org.gradle.work.Incremental;

import java.lang.annotation.Annotation;
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
        OutputFiles.class
    );

    @VisibleForTesting
    public static final ImmutableSet<Class<? extends Annotation>> IGNORED_METHOD_ANNOTATIONS = ImmutableSet.of(
        Internal.class,
        ReplacedBy.class
    );

    TypeAnnotationMetadataStore createAnnotationMetadataStore(CrossBuildInMemoryCacheFactory cacheFactory) {
        @SuppressWarnings("deprecation")
        Class<?> deprecatedAbstractTask = org.gradle.api.internal.AbstractTask.class;
        return new DefaultTypeAnnotationMetadataStore(
            ImmutableSet.of(
                CacheableTask.class,
                CacheableTransform.class
            ),
            ModifierAnnotationCategory.asMap(PROPERTY_TYPE_ANNOTATIONS),
            ImmutableSet.of(
                "java",
                "groovy",
                "kotlin"
            ),
            ImmutableSet.of(
                deprecatedAbstractTask,
                ClosureBackedAction.class,
                ConfigureUtil.WrappedConfigureAction.class,
                ConventionTask.class,
                Describable.class,
                DefaultDomainObjectCollection.class,
                DefaultDomainObjectSet.class,
                DefaultNamedDomainObjectCollection.class,
                DefaultNamedDomainObjectList.class,
                DefaultNamedDomainObjectSet.class,
                DefaultPolymorphicDomainObjectContainer.class,
                DefaultTask.class,
                DynamicObjectAware.class,
                ExtensionAware.class,
                HasConvention.class,
                IConventionAware.class,
                ScriptOrigin.class,
                Task.class
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

    TaskScheme createTaskScheme(InspectionSchemeFactory inspectionSchemeFactory, InstantiatorFactory instantiatorFactory) {
        InstantiationScheme instantiationScheme = instantiatorFactory.decorateScheme();
        InspectionScheme inspectionScheme = inspectionSchemeFactory.inspectionScheme(
            ImmutableSet.of(
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
                OptionValues.class
            ),
            ImmutableSet.of(
                Classpath.class,
                CompileClasspath.class,
                Incremental.class,
                Optional.class,
                PathSensitive.class,
                SkipWhenEmpty.class
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

    TypeAnnotationHandler createCacheableTaskAnnotationHandler() {
        return new CacheableTaskTypeAnnotationHandler();
    }

    PropertyAnnotationHandler createConsoleAnnotationHandler() {
        return new NoOpPropertyAnnotationHandler(Console.class);
    }

    PropertyAnnotationHandler createInternalAnnotationHandler() {
        return new NoOpPropertyAnnotationHandler(Internal.class);
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
        return new NestedBeanAnnotationHandler();
    }
}
