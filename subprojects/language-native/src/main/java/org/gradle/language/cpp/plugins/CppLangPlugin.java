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
package org.gradle.language.cpp.plugins;

import com.google.common.collect.Maps;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.language.base.internal.LanguageRegistry;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.cpp.CppSourceSet;
import org.gradle.language.cpp.internal.DefaultCppSourceSet;
import org.gradle.language.nativeplatform.internal.NativeLanguageRegistration;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.language.nativeplatform.internal.CompileTaskConfig;
import org.gradle.language.nativeplatform.internal.DefaultPreprocessingTool;

import java.util.Map;

/**
 * Adds core C++ language support.
 */
@Incubating
public class CppLangPlugin implements Plugin<ProjectInternal> {
    public void apply(final ProjectInternal project) {

        project.getPlugins().apply(ComponentModelBasePlugin.class);
        project.getExtensions().getByType(LanguageRegistry.class).add(new Cpp());
   }

    private static class Cpp extends NativeLanguageRegistration<CppSourceSet> {
        public String getName() {
            return "cpp";
        }

        public Class<CppSourceSet> getSourceSetType() {
            return CppSourceSet.class;
        }

        public Class<? extends CppSourceSet> getSourceSetImplementation() {
            return DefaultCppSourceSet.class;
        }

        public Map<String, Class<?>> getBinaryTools() {
            Map<String, Class<?>> tools = Maps.newLinkedHashMap();
            tools.put("cppCompiler", DefaultPreprocessingTool.class);
            return tools;
        }

        public SourceTransformTaskConfig getTransformTask() {
            return new CompileTaskConfig(this, CppCompile.class);
        }

    }
}
