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
package org.gradle.language.objectivec.plugins;

import com.google.common.collect.Maps;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.nativeplatform.internal.DependentSourceSetInternal;
import org.gradle.language.nativeplatform.internal.NativeLanguageTransform;
import org.gradle.language.nativeplatform.internal.PCHCompileTaskConfig;
import org.gradle.language.nativeplatform.internal.SourceCompileTaskConfig;
import org.gradle.language.objectivec.ObjectiveCSourceSet;
import org.gradle.language.objectivec.internal.DefaultObjectiveCSourceSet;
import org.gradle.language.objectivec.tasks.ObjectiveCCompile;
import org.gradle.language.objectivec.tasks.ObjectiveCPreCompiledHeaderCompile;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.internal.DefaultPreprocessingTool;
import org.gradle.nativeplatform.internal.pch.PchEnabledLanguageTransform;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;

import java.util.Map;

/**
 * Adds core Objective-C language support.
 */
@Incubating
public class ObjectiveCLangPlugin implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(ComponentModelBasePlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @ComponentType
        void registerLanguage(TypeBuilder<ObjectiveCSourceSet> builder) {
            builder.defaultImplementation(DefaultObjectiveCSourceSet.class);
            builder.internalView(DependentSourceSetInternal.class);
        }

        @Mutate
        void registerLanguageTransform(LanguageTransformContainer languages, ServiceRegistry serviceRegistry) {
            languages.add(new ObjectiveC());
        }
    }

    private static class ObjectiveC extends NativeLanguageTransform<ObjectiveCSourceSet> implements PchEnabledLanguageTransform<ObjectiveCSourceSet> {
        @Override
        public Class<ObjectiveCSourceSet> getSourceSetType() {
            return ObjectiveCSourceSet.class;
        }

        @Override
        public Map<String, Class<?>> getBinaryTools() {
            Map<String, Class<?>> tools = Maps.newLinkedHashMap();
            tools.put("objcCompiler", DefaultPreprocessingTool.class);
            return tools;
        }

        @Override
        public String getLanguageName() {
            return "objc";
        }

        @Override
        public ToolType getToolType() {
            return ToolType.OBJECTIVEC_COMPILER;
        }

        @Override
        public SourceTransformTaskConfig getTransformTask() {
            return new SourceCompileTaskConfig(this, ObjectiveCCompile.class);
        }

        @Override
        public SourceTransformTaskConfig getPchTransformTask() {
            return new PCHCompileTaskConfig(this, ObjectiveCPreCompiledHeaderCompile.class);
        }
    }
}
