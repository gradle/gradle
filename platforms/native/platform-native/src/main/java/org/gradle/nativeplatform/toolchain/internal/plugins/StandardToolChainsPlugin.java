/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.nativeplatform.toolchain.plugins.ClangCompilerPlugin;
import org.gradle.nativeplatform.toolchain.plugins.GccCompilerPlugin;
import org.gradle.nativeplatform.toolchain.plugins.MicrosoftVisualCppCompilerPlugin;

/**
 * Registers the standard tool chains.
 */
public abstract class StandardToolChainsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(MicrosoftVisualCppCompilerPlugin.class);
        project.getPluginManager().apply(GccCompilerPlugin.class);
        project.getPluginManager().apply(ClangCompilerPlugin.class);
    }
}
