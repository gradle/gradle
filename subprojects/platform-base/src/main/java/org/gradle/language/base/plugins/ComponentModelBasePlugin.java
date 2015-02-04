/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.language.base.plugins;

import org.gradle.api.*;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.registry.*;
import org.gradle.model.*;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.collection.internal.BridgedCollections;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.platform.base.PlatformContainer;
import org.gradle.platform.base.internal.*;

import javax.inject.Inject;

import static org.apache.commons.lang.StringUtils.capitalize;

/**
 * Base plugin for language support.
 *
 * Adds a {@link org.gradle.platform.base.ComponentSpecContainer} named {@code componentSpecs} to the project.
 *
 * For each binary instance added to the binaries container, registers a lifecycle task to create that binary.
 */
@Incubating
public class ComponentModelBasePlugin implements Plugin<ProjectInternal> {

    private final Instantiator instantiator;
    private final ModelRegistry modelRegistry;

    @Inject
    public ComponentModelBasePlugin(Instantiator instantiator, ModelRegistry modelRegistry) {
        this.instantiator = instantiator;
        this.modelRegistry = modelRegistry;
    }

    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(LanguageBasePlugin.class);

        // TODO:DAZ Remove this extension: will first need to change ComponentTypeRuleDefinitionHandler not to access ComponentSpecContainer via extension
        DefaultComponentSpecContainer components = project.getExtensions().create("componentSpecs", DefaultComponentSpecContainer.class, instantiator);
        String descriptor = ComponentModelBasePlugin.class.getName() + ".apply()";
        BridgedCollections.dynamicTypes(
                modelRegistry,
                ModelPath.path("components"),
                descriptor,
                ModelType.of(DefaultComponentSpecContainer.class),
                ModelType.of(DefaultComponentSpecContainer.class),
                ModelType.of(ComponentSpec.class),
                components,
                Named.Namer.INSTANCE,
                BridgedCollections.itemDescriptor(descriptor)
        );
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @Model
        LanguageRegistry languages(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(Instantiator.class).newInstance(DefaultLanguageRegistry.class);
        }

        @Model
        LanguageTransformContainer languageTransforms(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(Instantiator.class).newInstance(DefaultLanguageTransformContainer.class);
        }

        @Defaults
        void initializeSourceSetsForComponents(final CollectionBuilder<ComponentSpec> components, LanguageRegistry languageRegistry, LanguageTransformContainer languageTransforms) {
            for (LanguageRegistration<?> languageRegistration : languageRegistry) {
                // TODO - allow beforeEach() to be applied to internal types
                components.beforeEach(ComponentSourcesRegistrationAction.create(languageRegistration, languageTransforms));
            }
        }

        // Required because creation of Binaries from Components is not yet wired into the infrastructure
        @Mutate
        void closeComponentsForBinaries(CollectionBuilder<Task> tasks, ComponentSpecContainer components) {
        }

        // Finalizing here, as we need this to run after any 'assembling' task (jar, link, etc) is created.
        @Finalize
        void createSourceTransformTasks(final TaskContainer tasks, final BinaryContainer binaries, LanguageTransformContainer languageTransforms) {
            for (LanguageTransform<?, ?> language : languageTransforms) {
                for (final BinarySpecInternal binary : binaries.withType(BinarySpecInternal.class)) {
                    if (binary.isLegacyBinary() || !language.applyToBinary(binary)) {
                        continue;
                    }

                    final SourceTransformTaskConfig taskConfig = language.getTransformTask();
                    binary.getSource().withType(language.getSourceSetType(), new Action<LanguageSourceSet>() {
                        public void execute(LanguageSourceSet languageSourceSet) {
                            LanguageSourceSetInternal sourceSet = (LanguageSourceSetInternal) languageSourceSet;
                            if (sourceSet.getMayHaveSources()) {
                                String taskName = taskConfig.getTaskPrefix() + capitalize(binary.getName()) + capitalize(sourceSet.getFullName());
                                Task task = tasks.create(taskName, taskConfig.getTaskType());

                                taskConfig.configureTask(task, binary, sourceSet);

                                task.dependsOn(sourceSet);
                                binary.getTasks().add(task);
                            }
                        }
                    });
                }
            }
        }

        @Mutate
        void applyDefaultSourceConventions(CollectionBuilder<ComponentSpec> componentSpecs) {
            componentSpecs.afterEach(new Action<ComponentSpec>() {
                @Override
                public void execute(ComponentSpec componentSpec) {
                    for (LanguageSourceSet languageSourceSet : componentSpec.getSource()) {
                        // Only apply default locations when none explicitly configured
                        if (languageSourceSet.getSource().getSrcDirs().isEmpty()) {
                            languageSourceSet.getSource().srcDir(String.format("src/%s/%s", componentSpec.getName(), languageSourceSet.getName()));
                        }
                    }
                }
            });
        }

        // TODO:DAZ Work out why this is required
        @Mutate
        void closeSourcesForBinaries(BinaryContainer binaries, ProjectSourceSet sources) {
            // Only required because sources aren't fully integrated into model
        }

        @Model
        PlatformContainer platforms(ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            return instantiator.newInstance(DefaultPlatformContainer.class, instantiator);
        }

        @Model
        PlatformResolvers platformResolver(PlatformContainer platforms, ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            return instantiator.newInstance(DefaultPlatformResolvers.class, platforms);
        }

        @Mutate
        void registerPlatformExtension(ExtensionContainer extensions, PlatformContainer platforms) {
            extensions.add("platforms", platforms);
        }

    }

    // TODO:DAZ Needs to be a separate action since can't have parameterized utility methods in a RuleSource
    private static class ComponentSourcesRegistrationAction<U extends LanguageSourceSet> implements Action<ComponentSpec> {
        private final LanguageRegistration<U> languageRegistration;
        private final LanguageTransformContainer languageTransforms;

        private ComponentSourcesRegistrationAction(LanguageRegistration<U> registration, LanguageTransformContainer languageTransforms) {
            this.languageRegistration = registration;
            this.languageTransforms = languageTransforms;
        }

        public static <U extends LanguageSourceSet> ComponentSourcesRegistrationAction<U> create(LanguageRegistration<U> registration, LanguageTransformContainer languageTransforms) {
            return new ComponentSourcesRegistrationAction<U>(registration, languageTransforms);
        }

        public void execute(ComponentSpec componentSpec) {
            ComponentSpecInternal componentSpecInternal = (ComponentSpecInternal) componentSpec;
            registerLanguageSourceSetFactory(componentSpecInternal);
            createDefaultSourceSetForComponents(componentSpecInternal);
        }

        void registerLanguageSourceSetFactory(final ComponentSpecInternal component) {
            final FunctionalSourceSet functionalSourceSet = component.getSources();
            NamedDomainObjectFactory<? extends U> sourceSetFactory = languageRegistration.getSourceSetFactory(functionalSourceSet.getName());
            functionalSourceSet.registerFactory(languageRegistration.getSourceSetType(), sourceSetFactory);
        }

        // If there is a transform for the language into one of the component inputs, add a default source set
        void createDefaultSourceSetForComponents(final ComponentSpecInternal component) {
            final FunctionalSourceSet functionalSourceSet = component.getSources();
            for (LanguageTransform<?, ?> languageTransform : languageTransforms) {
                if (languageTransform.getSourceSetType().equals(languageRegistration.getSourceSetType())
                        && component.getInputTypes().contains(languageTransform.getOutputType())) {
                    functionalSourceSet.maybeCreate(languageRegistration.getName(), languageRegistration.getSourceSetType());
                    return;
                }
            }
        }
    }
}
