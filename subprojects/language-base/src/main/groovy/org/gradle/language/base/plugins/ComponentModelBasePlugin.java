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
import org.gradle.api.tasks.TaskContainer;
import org.gradle.configuration.project.ProjectConfigurationActionContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultLanguageRegistry;
import org.gradle.language.base.internal.LanguageRegistration;
import org.gradle.language.base.internal.LanguageRegistry;
import org.gradle.language.base.internal.plugins.ApplyDefaultSourceLocations;
import org.gradle.language.base.internal.plugins.CreateSourceTransformTask;
import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.base.ProjectComponent;
import org.gradle.runtime.base.ProjectComponentContainer;
import org.gradle.runtime.base.internal.DefaultProjectComponentContainer;
import org.gradle.runtime.base.internal.ProjectBinaryInternal;

import javax.inject.Inject;

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
    // TODO:DAZ This should be a model rule, once sourceSets are included in the model
    private final ProjectConfigurationActionContainer configurationActions;

    @Inject
    public ComponentModelBasePlugin(Instantiator instantiator, ProjectConfigurationActionContainer configurationActions) {
        this.instantiator = instantiator;
        this.configurationActions = configurationActions;
    }

    public void apply(final Project project) {
        project.getPlugins().apply(LanguageBasePlugin.class);

        LanguageRegistry languageRegistry = project.getExtensions().create("languages", DefaultLanguageRegistry.class);
        ProjectComponentContainer components = project.getExtensions().create("projectComponents", DefaultProjectComponentContainer.class, instantiator);
        BinaryContainer binaries = project.getExtensions().getByType(BinaryContainer.class);
        ProjectSourceSet sources = project.getExtensions().getByType(ProjectSourceSet.class);

        // TODO:DAZ Convert to model rules
        createSourceTransformTasks(project.getTasks(), binaries, languageRegistry);
        createProjectSourceSetForEachComponent(sources, components);
        createLanguageSourceSets(project, languageRegistry, sources);

        configurationActions.add(new ApplyDefaultSourceLocations());
    }

    private void createSourceTransformTasks(final TaskContainer tasks, final BinaryContainer binaries, LanguageRegistry languageRegistry) {
        languageRegistry.all(new Action<LanguageRegistration>() {
            public void execute(final LanguageRegistration language) {
                binaries.withType(ProjectBinaryInternal.class).all(new Action<ProjectBinaryInternal>() {
                    public void execute(ProjectBinaryInternal binary) {
                        final CreateSourceTransformTask createRule = new CreateSourceTransformTask(language);
                        createRule.createCompileTasksForBinary(tasks, binary);
                    }
                });
            }
        });
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

    private void createProjectSourceSetForEachComponent(final ProjectSourceSet sources, ProjectComponentContainer components) {
        // Create a functionalSourceSet for each native component, with the same name
        components.withType(ProjectComponent.class).all(new Action<ProjectComponent>() {
            public void execute(ProjectComponent component) {
                component.source(sources.maybeCreate(component.getName()));
            }
        });
    }
}
