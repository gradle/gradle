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

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.internal.JvmAssembly;
import org.gradle.jvm.internal.WithJvmAssembly;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.jvm.JvmResourceSet;
import org.gradle.language.jvm.internal.DefaultJvmResourceLanguageSourceSet;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;

import java.util.Collections;
import java.util.Map;

import static org.gradle.util.CollectionUtils.first;

/**
 * Plugin for packaging JVM resources. Applies the {@link org.gradle.language.base.plugins.ComponentModelBasePlugin}. Registers "resources" language support with the {@link
 * org.gradle.language.jvm.JvmResourceSet}.
 */
public class JvmResourcesPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(ComponentModelBasePlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @ComponentType
        void registerLanguage(TypeBuilder<JvmResourceSet> builder) {
            builder.defaultImplementation(DefaultJvmResourceLanguageSourceSet.class);
        }

        @Mutate
        void registerLanguageTransform(LanguageTransformContainer languages, ServiceRegistry serviceRegistry) {
            languages.add(new JvmResources());
        }
    }

    private static class JvmResources implements LanguageTransform<JvmResourceSet, org.gradle.jvm.JvmResources> {
        @Override
        public String getLanguageName() {
            return "resources";
        }

        @Override
        public Class<JvmResourceSet> getSourceSetType() {
            return JvmResourceSet.class;
        }

        @Override
        public Map<String, Class<?>> getBinaryTools() {
            return Collections.emptyMap();
        }

        @Override
        public Class<org.gradle.jvm.JvmResources> getOutputType() {
            return org.gradle.jvm.JvmResources.class;
        }

        @Override
        public SourceTransformTaskConfig getTransformTask() {
            return new SourceTransformTaskConfig() {
                @Override
                public String getTaskPrefix() {
                    return "process";
                }

                @Override
                public Class<? extends DefaultTask> getTaskType() {
                    return ProcessResources.class;
                }

                @Override
                public void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet, ServiceRegistry serviceRegistry) {
                    ProcessResources resourcesTask = (ProcessResources) task;
                    JvmResourceSet resourceSet = (JvmResourceSet) sourceSet;
                    resourcesTask.from(resourceSet.getSource());

                    // The first directory is the one created by JvmComponentPlugin.configureJvmBinaries()
                    // to be used as the default output directory for processed resources
                    JvmAssembly assembly = ((WithJvmAssembly) binary).getAssembly();
                    resourcesTask.setDestinationDir(first(assembly.getResourceDirectories()));

                    assembly.builtBy(resourcesTask);
                }
            };
        }
        @Override
        public boolean applyToBinary(BinarySpec binary) {
            return binary instanceof WithJvmAssembly;
        }
    }
}
