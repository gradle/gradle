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
import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.base.ProjectComponent;
import org.gradle.runtime.base.ProjectComponentContainer;
import org.gradle.runtime.base.internal.DefaultProjectComponentContainer;
import org.gradle.runtime.base.internal.ProjectBinaryInternal;
import org.gradle.util.CollectionUtils;

import javax.inject.Inject;
import java.util.Set;

/**
 * Base plugin for language support.
 *
 * Adds a {@link org.gradle.runtime.base.ProjectComponentContainer} named {@code projectComponents} to the project.
 * Adds a {@link org.gradle.runtime.base.BinaryContainer} named {@code binaries} to the project.
 * Adds a {@link org.gradle.language.base.ProjectSourceSet} named {@code sources} to the project.
 *
 * For each binary instance added to the binaries container, registers a lifecycle task to create that binary.
 */
@Incubating
public class ComponentModelBasePlugin implements Plugin<Project> {

    private final Instantiator instantiator;

    @Inject
    public ComponentModelBasePlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public void apply(final Project project) {
        project.getPlugins().apply(LanguageBasePlugin.class);

        LanguageRegistry languageRegistry = project.getExtensions().create("languages", DefaultLanguageRegistry.class);
        project.getExtensions().create("projectComponents", DefaultProjectComponentContainer.class, instantiator);
        ProjectSourceSet sources = project.getExtensions().getByType(ProjectSourceSet.class);

        // TODO:DAZ Convert to model rules
        createLanguageSourceSets(project, languageRegistry, sources);
    }

    private void createLanguageSourceSets(final Project project, final LanguageRegistry languageRegistry, final ProjectSourceSet sources) {
        languageRegistry.all(new Action<LanguageRegistration>() {
            public void execute(final LanguageRegistration languageRegistration) {
                sources.all(new Action<FunctionalSourceSet>() {
                    public void execute(final FunctionalSourceSet functionalSourceSet) {
                        NamedDomainObjectFactory<? extends LanguageSourceSet> namedDomainObjectFactory = new NamedDomainObjectFactory<LanguageSourceSet>() {
                            public LanguageSourceSet create(String name) {
                                Class<? extends LanguageSourceSet> sourceSetImplementation = languageRegistration.getSourceSetImplementation();
                                return instantiator.newInstance(sourceSetImplementation, name, functionalSourceSet, project);
                            }
                        };
                        Class<? extends LanguageSourceSet> sourceSetType = languageRegistration.getSourceSetType();
                        functionalSourceSet.registerFactory((Class<LanguageSourceSet>) sourceSetType, namedDomainObjectFactory);

                        // Create a default language source set
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
        ProjectComponentContainer projectComponents(ExtensionContainer extensions) {
            return extensions.getByType(ProjectComponentContainer.class);
        }

        @Model
        Set<String> projectComponentNames(ExtensionContainer extensions) {
            ProjectComponentContainer components = extensions.getByType(ProjectComponentContainer.class);
            return CollectionUtils.collect(components, new Transformer<String, ProjectComponent>() {
                public String transform(ProjectComponent original) {
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
        void attachFunctionalSourceSetToComponents(ProjectComponentContainer components, ProjectSourceSet sources) {
            for (ProjectComponent component : components) {
                component.source(sources.getByName(component.getName()));
            }
        }

        // Finalizing here, as we need this to run after any 'assembling' task (jar, link, etc) is created.
        @Finalize
        void createSourceTransformTasks(final TaskContainer tasks, final BinaryContainer binaries, LanguageRegistry languageRegistry) {
            for (LanguageRegistration language : languageRegistry) {
                for (ProjectBinaryInternal binary : binaries.withType(ProjectBinaryInternal.class)) {
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
