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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.internal.registry.LanguageRegistry;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.nativeplatform.internal.CompileTaskConfig;
import org.gradle.language.nativeplatform.internal.DefaultPreprocessingTool;
import org.gradle.language.nativeplatform.internal.NativeLanguageRegistration;
import org.gradle.language.objectivec.ObjectiveCSourceSet;
import org.gradle.language.objectivec.internal.DefaultObjectiveCSourceSet;
import org.gradle.language.objectivec.tasks.ObjectiveCCompile;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;

import java.util.Map;

/**
 * Adds core Objective-C language support.
 */
@Incubating
public class ObjectiveCLangPlugin implements Plugin<Project> {
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
        void registerLanguage(LanguageRegistry languages, ServiceRegistry serviceRegistry) {
            languages.add(new ObjectiveC(serviceRegistry.get(Instantiator.class), serviceRegistry.get(FileResolver.class)));
        }

        @Mutate
        void registerLanguageTransform(LanguageTransformContainer languages, ServiceRegistry serviceRegistry) {
            languages.add(new ObjectiveC(serviceRegistry.get(Instantiator.class), serviceRegistry.get(FileResolver.class)));
        }
    }

    private static class ObjectiveC extends NativeLanguageRegistration<ObjectiveCSourceSet> {
        public ObjectiveC(Instantiator instantiator, FileResolver fileResolver) {
            super(instantiator, fileResolver);
        }

        public String getName() {
            return "objc";
        }

        public Class<ObjectiveCSourceSet> getSourceSetType() {
            return ObjectiveCSourceSet.class;
        }

        protected Class<? extends ObjectiveCSourceSet> getSourceSetImplementation() {
            return DefaultObjectiveCSourceSet.class;
        }

        public Map<String, Class<?>> getBinaryTools() {
            Map<String, Class<?>> tools = Maps.newLinkedHashMap();
            tools.put("objcCompiler", DefaultPreprocessingTool.class);
            return tools;
        }

        public SourceTransformTaskConfig getTransformTask() {
            return new CompileTaskConfig(this, ObjectiveCCompile.class);
        }

    }
}
