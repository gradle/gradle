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
import org.gradle.runtime.base.ProjectComponentContainer;
import org.gradle.runtime.base.internal.DefaultBinaryNamingSchemeBuilder;
import org.gradle.runtime.jvm.JvmLibrary;
import org.gradle.runtime.jvm.internal.DefaultJvmLibrary;
import org.gradle.runtime.jvm.internal.plugins.CreateJvmBinaries;
import org.gradle.runtime.jvm.internal.plugins.CreateTasksForJvmBinaries;
import org.gradle.runtime.jvm.internal.plugins.DefaultJvmComponentExtension;

import javax.inject.Inject;

/**
 * Base plugin for JVM component support. Applies the {@link org.gradle.language.base.plugins.LanguageBasePlugin}.
 * Registers the {@link org.gradle.runtime.jvm.JvmLibrary} library type for the {@link org.gradle.runtime.base.ProjectComponentContainer}.
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

        ProjectComponentContainer softwareComponents = project.getExtensions().getByType(ProjectComponentContainer.class);
        softwareComponents.registerFactory(JvmLibrary.class, new NamedDomainObjectFactory<JvmLibrary>() {
            public JvmLibrary create(String name) {
                return new DefaultJvmLibrary(name);
            }
        });

        NamedDomainObjectContainer<JvmLibrary> jvmLibraries = softwareComponents.containerWithType(JvmLibrary.class);
        project.getExtensions().create("jvm", DefaultJvmComponentExtension.class, jvmLibraries);

        modelRules.register("jvm.libraries", jvmLibraries);

        modelRules.rule(new CreateJvmBinaries(new DefaultBinaryNamingSchemeBuilder(), project.getBuildDir()));
        modelRules.rule(new CreateTasksForJvmBinaries());
    }
}
