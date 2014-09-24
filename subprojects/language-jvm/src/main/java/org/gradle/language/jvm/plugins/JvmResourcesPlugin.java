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
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageRegistration;
import org.gradle.language.base.internal.LanguageRegistry;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.jvm.JvmResourceSet;
import org.gradle.language.jvm.internal.DefaultJvmResourceSet;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.TransformationFileType;

import java.util.Collections;
import java.util.Map;

/**
 * Plugin for packaging JVM resources. Applies the {@link org.gradle.language.base.plugins.ComponentModelBasePlugin}. Registers "resources" language support with the {@link
 * org.gradle.language.jvm.JvmResourceSet}.
 */
@Incubating
public class JvmResourcesPlugin implements Plugin<Project> {

    public void apply(final Project project) {
        project.getPlugins().apply(ComponentModelBasePlugin.class);
        project.getExtensions().getByType(LanguageRegistry.class).add(new JvmResources());
    }

    private static class JvmResources implements LanguageRegistration<JvmResourceSet> {
        public String getName() {
            return "resources";
        }

        public Class<JvmResourceSet> getSourceSetType() {
            return JvmResourceSet.class;
        }

        public Class<? extends JvmResourceSet> getSourceSetImplementation() {
            return DefaultJvmResourceSet.class;
        }

        public Map<String, Class<?>> getBinaryTools() {
            return Collections.emptyMap();
        }

        public Class<? extends TransformationFileType> getOutputType() {
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
