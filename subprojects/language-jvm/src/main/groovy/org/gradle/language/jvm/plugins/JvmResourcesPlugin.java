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

package org.gradle.language.jvm.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.internal.LanguageRegistry;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.jvm.ResourceSet;
import org.gradle.language.jvm.internal.DefaultResourceSet;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.jvm.internal.ProjectJarBinaryInternal;

/**
 * Plugin for packaging JVM resources. Applies the {@link org.gradle.language.base.plugins.ComponentModelBasePlugin}. Registers "resources" language support with the {@link
 * org.gradle.language.jvm.ResourceSet}.
 */
public class JvmResourcesPlugin implements Plugin<Project> {

    public void apply(final Project project) {
        project.getPlugins().apply(ComponentModelBasePlugin.class);
        project.getExtensions().getByType(LanguageRegistry.class).registerLanguage("resources", ResourceSet.class, DefaultResourceSet.class);
    }

    /**
     * Model rules.
     */
    @RuleSource
    static class CreateProcessResourcesTasks {
        @SuppressWarnings("UnusedDeclaration")
        @Mutate
        void createTasks(final TaskContainer tasks, BinaryContainer binaries) {
            // TODO:DAZ Make this apply to all types of ProjectJvmLibraryBinary
            for (ProjectJarBinaryInternal binary : binaries.withType(ProjectJarBinaryInternal.class)) {
                for (ResourceSet resourceSet : binary.getSource().withType(ResourceSet.class)) {

                    String resourcesTaskName = binary.getNamingScheme().getTaskName("process", ((LanguageSourceSetInternal) resourceSet).getFullName());
                    ProcessResources resourcesTask = tasks.create(resourcesTaskName, ProcessResources.class);
                    resourcesTask.from(resourceSet.getSource());
                    resourcesTask.setDestinationDir(binary.getResourcesDir());

                    binary.getTasks().add(resourcesTask);
                    binary.getTasks().getJar().dependsOn(resourcesTask);
                }
            }
        }
    }
}
