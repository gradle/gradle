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
package org.gradle.language.objectivecpp.plugins;

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
import org.gradle.language.objectivecpp.ObjectiveCppSourceSet;
import org.gradle.language.objectivecpp.internal.DefaultObjectiveCppSourceSet;
import org.gradle.language.objectivecpp.tasks.ObjectiveCppCompile;
import org.gradle.language.objectivecpp.tasks.ObjectiveCppPreCompiledHeaderCompile;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.internal.DefaultPreprocessingTool;
import org.gradle.nativeplatform.internal.pch.PchEnabledLanguageTransform;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;

import java.util.Map;

/**
 * Adds core Objective-Cpp language support.
 */
@Incubating
public class ObjectiveCppLangPlugin implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(ComponentModelBasePlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @ComponentType
        void registerLanguage(TypeBuilder<ObjectiveCppSourceSet> builder) {
            builder.defaultImplementation(DefaultObjectiveCppSourceSet.class);
            builder.internalView(DependentSourceSetInternal.class);
        }

        @Mutate
        void registerLanguageTransform(LanguageTransformContainer languages, ServiceRegistry serviceRegistry) {
            languages.add(new ObjectiveCpp());
        }
    }

    private static class ObjectiveCpp extends NativeLanguageTransform<ObjectiveCppSourceSet> implements PchEnabledLanguageTransform<ObjectiveCppSourceSet> {
        @Override
        public Class<ObjectiveCppSourceSet> getSourceSetType() {
            return ObjectiveCppSourceSet.class;
        }

        @Override
        public Map<String, Class<?>> getBinaryTools() {
            Map<String, Class<?>> tools = Maps.newLinkedHashMap();
            tools.put("objcppCompiler", DefaultPreprocessingTool.class);
            return tools;
        }

        @Override
        public String getLanguageName() {
            return "objcpp";
        }

        @Override
        public ToolType getToolType() {
            return ToolType.OBJECTIVECPP_COMPILER;
        }

        @Override
        public SourceTransformTaskConfig getTransformTask() {
            return new SourceCompileTaskConfig(this, ObjectiveCppCompile.class);
        }

        @Override
        public SourceTransformTaskConfig getPchTransformTask() {
            return new PCHCompileTaskConfig(this, ObjectiveCppPreCompiledHeaderCompile.class);
        }
    }
}
