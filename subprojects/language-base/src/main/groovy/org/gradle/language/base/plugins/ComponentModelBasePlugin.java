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
import org.gradle.configuration.project.ProjectConfigurationActionContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultLanguageRegistry;
import org.gradle.language.base.internal.LanguageRegistration;
import org.gradle.language.base.internal.LanguageRegistry;
import org.gradle.language.base.internal.plugins.ApplyDefaultSourceLocations;
import org.gradle.runtime.base.ProjectComponent;
import org.gradle.runtime.base.ProjectComponentContainer;
import org.gradle.runtime.base.internal.DefaultProjectComponentContainer;

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

    public void apply(final Project target) {
        target.getPlugins().apply(LanguageBasePlugin.class);

        LanguageRegistry languageRegistry = target.getExtensions().create("languages", DefaultLanguageRegistry.class);
        ProjectComponentContainer components = target.getExtensions().create("projectComponents", DefaultProjectComponentContainer.class, instantiator);
        ProjectSourceSet sources = target.getExtensions().getByType(ProjectSourceSet.class);

        createProjectSourceSetForEachComponent(sources, components);
        createLanguageSourceSets(target, languageRegistry, sources);

        configurationActions.add(new ApplyDefaultSourceLocations());
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
