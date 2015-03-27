/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.cpp.CppSourceSet;
import org.gradle.language.cpp.tasks.CppPreCompiledHeaderCompile;
import org.gradle.language.nativeplatform.internal.DefaultPreprocessingTool;
import org.gradle.language.nativeplatform.internal.NativeLanguageTransform;
import org.gradle.language.nativeplatform.internal.PCHCompileTaskConfig;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.internal.pch.PreCompiledHeaderTransformContainer;

import java.util.Map;

/**
 * Adds support for compiling C++ pre-compiled headers.
 */
@SuppressWarnings("UnusedDeclaration")
public class CppLangPCHPlugin extends RuleSource {
    @Mutate
    void registerPreCompiledHeaderTask(PreCompiledHeaderTransformContainer pchTransformContainer) {
        pchTransformContainer.add(new CppPCH());
    }


    private static class CppPCH extends NativeLanguageTransform<CppSourceSet> {
        public Class<CppSourceSet> getSourceSetType() {
            return CppSourceSet.class;
        }

        public Map<String, Class<?>> getBinaryTools() {
            Map<String, Class<?>> tools = Maps.newLinkedHashMap();
            tools.put("cppCompiler", DefaultPreprocessingTool.class);
            return tools;
        }

        @Override
        public SourceTransformTaskConfig getTransformTask() {
            return new PCHCompileTaskConfig(this, CppPreCompiledHeaderCompile.class);
        }
    }
}
