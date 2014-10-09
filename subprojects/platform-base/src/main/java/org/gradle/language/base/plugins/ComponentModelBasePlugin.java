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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultLanguageRegistry;
import org.gradle.language.base.internal.LanguageRegistration;
import org.gradle.language.base.internal.LanguageRegistry;
import org.gradle.language.base.internal.plugins.CreateSourceTransformTask;
import org.gradle.model.Finalize;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.ModelCreators;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.PolymorphicDomainObjectContainerModelProjection;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.platform.base.internal.DefaultComponentSpecContainer;
import org.gradle.platform.base.internal.DefaultPlatformContainer;

import javax.inject.Inject;

/**
 * Base plugin for language support.
 *
 * Adds a {@link org.gradle.platform.base.ComponentSpecContainer} named {@code componentSpecs} to the project. Adds a {@link org.gradle.language.base.ProjectSourceSet} named {@code sources} to the
 * project.
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
        project.getPlugins().apply(LanguageBasePlugin.class);

        LanguageRegistry languageRegistry = project.getExtensions().create("languages", DefaultLanguageRegistry.class);
        ProjectSourceSet sources = project.getExtensions().getByType(ProjectSourceSet.class);

        DefaultComponentSpecContainer components = project.getExtensions().create("componentSpecs", DefaultComponentSpecContainer.class, instantiator);
        modelRegistry.create(
                ModelCreators.of(ModelReference.of("componentSpecs", DefaultComponentSpecContainer.class), components)
                        .simpleDescriptor("Project.<init>.componentSpecs()")
                        .withProjection(new PolymorphicDomainObjectContainerModelProjection<DefaultComponentSpecContainer, ComponentSpec>(components, ComponentSpec.class))
                        .build()
                        );

        // TODO:DAZ Convert to model rules
        createLanguageSourceSets(sources, components, languageRegistry, project.getFileResolver());
    }

    private void createLanguageSourceSets(final ProjectSourceSet sources, final ComponentSpecContainer components, final LanguageRegistry languageRegistry, final FileResolver fileResolver) {
        languageRegistry.all(new Action<LanguageRegistration<?>>() {
            public void execute(final LanguageRegistration<?> languageRegistration) {
                registerLanguageSourceSetFactory(languageRegistration, sources, fileResolver);
                createDefaultSourceSetForComponents(languageRegistration, components);
            }
        });
    }

    private <U extends LanguageSourceSet> void createDefaultSourceSetForComponents(final LanguageRegistration<U> languageRegistration, ComponentSpecContainer components) {
        components.withType(ComponentSpecInternal.class).all(new Action<ComponentSpecInternal>() {
            public void execute(final ComponentSpecInternal componentSpecInternal) {
                final FunctionalSourceSet functionalSourceSet = componentSpecInternal.getMainSource();
                if (componentSpecInternal.getInputTypes().contains(languageRegistration.getOutputType())) {
                    functionalSourceSet.maybeCreate(languageRegistration.getName(), languageRegistration.getSourceSetType());
                }
            }
        });
    }

    private <U extends LanguageSourceSet> void registerLanguageSourceSetFactory(final LanguageRegistration<U> languageRegistration, ProjectSourceSet sources, final FileResolver fileResolver) {
        sources.all(new Action<FunctionalSourceSet>() {
            public void execute(final FunctionalSourceSet functionalSourceSet) {
                NamedDomainObjectFactory<U> namedDomainObjectFactory = new NamedDomainObjectFactory<U>() {
                    public U create(String name) {
                        Class<? extends U> sourceSetImplementation = languageRegistration.getSourceSetImplementation();
                        return instantiator.newInstance(sourceSetImplementation, name, functionalSourceSet, fileResolver);
                    }
                };
                functionalSourceSet.registerFactory(languageRegistration.getSourceSetType(), namedDomainObjectFactory);
            }
        });
    }

    /**
     * Model rules.
     */
    @SuppressWarnings("UnusedDeclaration")
    @RuleSource
    static class Rules {
        @Model
        LanguageRegistry languages(ExtensionContainer extensions) {
            return extensions.getByType(LanguageRegistry.class);
        }

        // Required because creation of Binaries from Components is not yet wired into the infrastructure
        @Mutate
        void closeComponentsForBinaries(CollectionBuilder<Task> tasks, ComponentSpecContainer components) {
        }

        // Finalizing here, as we need this to run after any 'assembling' task (jar, link, etc) is created.
        @Finalize
        void createSourceTransformTasks(final TaskContainer tasks, final BinaryContainer binaries, LanguageRegistry languageRegistry) {
            for (LanguageRegistration<?> language : languageRegistry) {
                for (BinarySpecInternal binary : binaries.withType(BinarySpecInternal.class)) {
                    final CreateSourceTransformTask createRule = new CreateSourceTransformTask(language);
                    createRule.createCompileTasksForBinary(tasks, binary);
                }
            }
        }

        @Finalize
        // Needs to run after NativeComponentModelPlugin.Rules.configureGeneratedSourceSets()
        void applyDefaultSourceConventions(ProjectSourceSet sources) {
            for (FunctionalSourceSet functionalSourceSet : sources) {
                for (LanguageSourceSet languageSourceSet : functionalSourceSet) {
                    // Only apply default locations when none explicitly configured
                    if (languageSourceSet.getSource().getSrcDirs().isEmpty()) {
                        languageSourceSet.getSource().srcDir(String.format("src/%s/%s", functionalSourceSet.getName(), languageSourceSet.getName()));
                    }
                }
            }
        }

        @Mutate
        void closeSourcesForBinaries(BinaryContainer binaries, ProjectSourceSet sources) {
            // Only required because sources aren't fully integrated into model
        }

        @Model
        PlatformContainer platforms(ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            return instantiator.newInstance(DefaultPlatformContainer.class, Platform.class, instantiator);
        }

        @Mutate
        void registerPlatformExtension(ExtensionContainer extensions, PlatformContainer platforms) {
            extensions.add("platforms", platforms);
        }

    }
}
