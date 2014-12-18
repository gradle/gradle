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
import org.gradle.language.base.internal.LanguageRegistry;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.nativeplatform.internal.CompileTaskConfig;
import org.gradle.language.nativeplatform.internal.DefaultPreprocessingTool;
import org.gradle.language.nativeplatform.internal.NativeLanguageRegistration;
import org.gradle.language.objectivecpp.ObjectiveCppSourceSet;
import org.gradle.language.objectivecpp.internal.DefaultObjectiveCppSourceSet;
import org.gradle.language.objectivecpp.tasks.ObjectiveCppCompile;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;

import java.util.Map;

/**
 * Adds core Objective-Cpp language support.
 */
@Incubating
public class ObjectiveCppLangPlugin implements Plugin<Project> {
    public void apply(final Project project) {
        project.getPluginManager().apply(ComponentModelBasePlugin.class);
    }

    /**
     * Model rules.
     */
    @SuppressWarnings("UnusedDeclaration")
    @RuleSource
    static class Rules {
        @Mutate
        void registerLanguage(LanguageRegistry languages) {
            languages.add(new ObjectiveCpp());
        }
    }

    private static class ObjectiveCpp extends NativeLanguageRegistration<ObjectiveCppSourceSet> {
        public String getName() {
            return "objcpp";
        }

        public Class<ObjectiveCppSourceSet> getSourceSetType() {
            return ObjectiveCppSourceSet.class;
        }

        public Class<? extends ObjectiveCppSourceSet> getSourceSetImplementation() {
            return DefaultObjectiveCppSourceSet.class;
        }

        public Map<String, Class<?>> getBinaryTools() {
            Map<String, Class<?>> tools = Maps.newLinkedHashMap();
            tools.put("objcppCompiler", DefaultPreprocessingTool.class);
            return tools;
        }

        public SourceTransformTaskConfig getTransformTask() {
            return new CompileTaskConfig(this, ObjectiveCppCompile.class);
        }

    }
}
