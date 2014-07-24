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
import org.gradle.api.internal.PolymorphicDomainObjectContainerModelAdapter;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultLanguageRegistry;
import org.gradle.language.base.internal.LanguageRegistration;
import org.gradle.language.base.internal.LanguageRegistry;
import org.gradle.language.base.internal.plugins.CreateSourceTransformTask;
import org.gradle.model.*;
import org.gradle.model.collection.NamedItemCollectionBuilder;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.base.ComponentSpec;
import org.gradle.runtime.base.ComponentSpecContainer;
import org.gradle.runtime.base.internal.BinarySpecInternal;
import org.gradle.runtime.base.internal.DefaultComponentSpecContainer;
import org.gradle.util.CollectionUtils;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Base plugin for language support.
 *
 * Adds a {@link org.gradle.runtime.base.ComponentSpecContainer} named {@code projectComponents} to the project.
 * Adds a {@link org.gradle.runtime.base.BinaryContainer} named {@code binaries} to the project.
 * Adds a {@link org.gradle.language.base.ProjectSourceSet} named {@code sources} to the project.
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

        DefaultComponentSpecContainer components = project.getExtensions().create("projectComponents", DefaultComponentSpecContainer.class, instantiator);
        final PolymorphicDomainObjectContainerModelAdapter<ComponentSpec, ComponentSpecContainer> componentSpecContainerAdapter = new PolymorphicDomainObjectContainerModelAdapter<ComponentSpec, ComponentSpecContainer>(
                components, ModelType.of(ComponentSpecContainer.class), ComponentSpec.class
        );

        modelRegistry.create(new ModelCreator() {
            public ModelPath getPath() {
                return ModelPath.path("projectComponents");
            }

            public ModelPromise getPromise() {
                return componentSpecContainerAdapter.asPromise();
            }

            public ModelAdapter create(Inputs inputs) {
                return componentSpecContainerAdapter;
            }

            public List<ModelReference<?>> getInputs() {
                return Collections.emptyList();
            }

            public ModelRuleDescriptor getDescriptor() {
                return new SimpleModelRuleDescriptor("Project.<init>.projectComponents()");
            }
        });

        // TODO:DAZ Convert to model rules
        createLanguageSourceSets(sources, languageRegistry, project.getFileResolver());
    }

    private void createLanguageSourceSets(final ProjectSourceSet sources, final LanguageRegistry languageRegistry, final FileResolver fileResolver) {
        languageRegistry.all(new Action<LanguageRegistration>() {
            public void execute(final LanguageRegistration languageRegistration) {
                sources.all(new Action<FunctionalSourceSet>() {
                    public void execute(final FunctionalSourceSet functionalSourceSet) {
                        NamedDomainObjectFactory<? extends LanguageSourceSet> namedDomainObjectFactory = new NamedDomainObjectFactory<LanguageSourceSet>() {
                            public LanguageSourceSet create(String name) {
                                Class<? extends LanguageSourceSet> sourceSetImplementation = languageRegistration.getSourceSetImplementation();
                                return instantiator.newInstance(sourceSetImplementation, name, functionalSourceSet, fileResolver);
                            }
                        };
                        Class<? extends LanguageSourceSet> sourceSetType = languageRegistration.getSourceSetType();
                        functionalSourceSet.registerFactory((Class<LanguageSourceSet>) sourceSetType, namedDomainObjectFactory);

                        // Create a default language source set
                        // TODO:DAZ Should only attach LanguageSourceSet where appropriate for component (at the moment we have no way to determine the component)
                        functionalSourceSet.maybeCreate(languageRegistration.getName(), sourceSetType);
                    }
                });
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

        @Model
        Set<String> projectComponentNames(ExtensionContainer extensions) {
            ComponentSpecContainer components = extensions.getByType(ComponentSpecContainer.class);
            return CollectionUtils.collect(components, new Transformer<String, ComponentSpec>() {
                public String transform(ComponentSpec original) {
                    return original.getName();
                }
            });
        }

        @Mutate
        void createProjectSourceSetForEachComponent(ProjectSourceSet sources, @Path("projectComponentNames") Set<String> componentNames) {
            // Create a functionalSourceSet for each native component, with the same name
            for (String componentName : componentNames) {
                sources.maybeCreate(componentName);
            }
        }

        @Mutate
        void attachFunctionalSourceSetToComponents(ComponentSpecContainer components, ProjectSourceSet sources) {
            for (ComponentSpec component : components) {
                component.source(sources.getByName(component.getName()));
            }
        }

        // Required because creation of Binaries from Components is not yet wired into the infrastructure
        @Mutate
        void closeComponentsForBinaries(NamedItemCollectionBuilder<Task> tasks, ComponentSpecContainer components) {}

        // Finalizing here, as we need this to run after any 'assembling' task (jar, link, etc) is created.
        @Finalize
        void createSourceTransformTasks(final TaskContainer tasks, final BinaryContainer binaries, LanguageRegistry languageRegistry) {
            for (LanguageRegistration language : languageRegistry) {
                for (BinarySpecInternal binary : binaries.withType(BinarySpecInternal.class)) {
                    final CreateSourceTransformTask createRule = new CreateSourceTransformTask(language);
                    createRule.createCompileTasksForBinary(tasks, binary);
                }
            }
        }

        @Finalize // Needs to run after NativeComponentModelPlugin.Rules.configureGeneratedSourceSets()
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
    }
}
