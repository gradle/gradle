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

import org.gradle.api.*;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.JvmBinarySpec;
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
import org.gradle.platform.base.LanguageType;
import org.gradle.platform.base.LanguageTypeBuilder;

import java.util.Collections;
import java.util.Map;

/**
 * Plugin for packaging JVM resources. Applies the {@link org.gradle.language.base.plugins.ComponentModelBasePlugin}. Registers "resources" language support with the {@link
 * org.gradle.language.jvm.JvmResourceSet}.
 */
@Incubating
public class JvmResourcesPlugin implements Plugin<Project> {

    public void apply(final Project project) {
        project.getPluginManager().apply(ComponentModelBasePlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @LanguageType
        void registerLanguage(LanguageTypeBuilder<JvmResourceSet> builder) {
            builder.setLanguageName("resources");
            builder.defaultImplementation(DefaultJvmResourceLanguageSourceSet.class);
        }

        @Mutate
        void registerLanguageTransform(LanguageTransformContainer languages, ServiceRegistry serviceRegistry) {
            languages.add(new JvmResources());
        }
    }

    private static class JvmResources implements LanguageTransform<JvmResourceSet, org.gradle.jvm.JvmResources> {
        public Class<JvmResourceSet> getSourceSetType() {
            return JvmResourceSet.class;
        }

        public Map<String, Class<?>> getBinaryTools() {
            return Collections.emptyMap();
        }

        public Class<org.gradle.jvm.JvmResources> getOutputType() {
            return org.gradle.jvm.JvmResources.class;
        }

        public SourceTransformTaskConfig getTransformTask() {
            return new SourceTransformTaskConfig() {
                public String getTaskPrefix() {
                    return "process";
                }

                public Class<? extends DefaultTask> getTaskType() {
                    return ProcessResources.class;
                }

                public void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet) {
                    ProcessResources resourcesTask = (ProcessResources) task;
                    JvmResourceSet resourceSet = (JvmResourceSet) sourceSet;
                    JvmBinarySpec jvmBinary = (JvmBinarySpec) binary;
                    resourcesTask.from(resourceSet.getSource());
                    resourcesTask.setDestinationDir(jvmBinary.getResourcesDir());
                    jvmBinary.getTasks().getJar().dependsOn(resourcesTask);
                }
            };
        }
        public boolean applyToBinary(BinarySpec binary) {
            return binary instanceof JvmBinarySpec;
        }
    }
}
