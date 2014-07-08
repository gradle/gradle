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
package org.gradle.runtime.jvm.plugins;

import org.gradle.api.*;
import org.gradle.language.base.plugins.LanguageBasePlugin;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.model.ModelRules;
import org.gradle.runtime.base.NamedProjectComponentIdentifier;
import org.gradle.runtime.base.ProjectComponentContainer;
import org.gradle.runtime.base.internal.DefaultBinaryNamingSchemeBuilder;
import org.gradle.runtime.base.internal.DefaultNamedProjectComponentIdentifier;
import org.gradle.runtime.jvm.ProjectJvmLibrary;
import org.gradle.runtime.jvm.internal.DefaultProjectJvmLibrary;
import org.gradle.runtime.jvm.internal.plugins.CreateJvmBinaries;
import org.gradle.runtime.jvm.internal.plugins.CreateTasksForJarBinaries;
import org.gradle.runtime.jvm.internal.plugins.DefaultJvmComponentExtension;

import javax.inject.Inject;

/**
 * Base plugin for JVM component support. Applies the {@link org.gradle.language.base.plugins.LanguageBasePlugin}.
 * Registers the {@link org.gradle.runtime.jvm.ProjectJvmLibrary} library type for the {@link org.gradle.runtime.base.ProjectComponentContainer}.
 */
@Incubating
public class JvmComponentPlugin implements Plugin<Project> {
    private final ModelRules modelRules;

    @Inject
    public JvmComponentPlugin(ModelRules modelRules) {
        this.modelRules = modelRules;
    }

    public void apply(final Project project) {
        project.getPlugins().apply(LifecycleBasePlugin.class);
        project.getPlugins().apply(LanguageBasePlugin.class);

        ProjectComponentContainer projectComponents = project.getExtensions().getByType(ProjectComponentContainer.class);
        projectComponents.registerFactory(ProjectJvmLibrary.class, new NamedDomainObjectFactory<ProjectJvmLibrary>() {
            public ProjectJvmLibrary create(String name) {
                NamedProjectComponentIdentifier id = new DefaultNamedProjectComponentIdentifier(project.getPath(), name);
                return new DefaultProjectJvmLibrary(id);
            }
        });

        NamedDomainObjectContainer<ProjectJvmLibrary> jvmLibraries = projectComponents.containerWithType(ProjectJvmLibrary.class);
        project.getExtensions().create("jvm", DefaultJvmComponentExtension.class, jvmLibraries);

        modelRules.register("jvm.libraries", jvmLibraries);

        modelRules.rule(new CreateJvmBinaries(new DefaultBinaryNamingSchemeBuilder(), project.getBuildDir()));
        modelRules.rule(new CreateTasksForJarBinaries());
    }
}
