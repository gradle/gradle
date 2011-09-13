/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal;

import org.gradle.api.internal.classpath.Module;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.classpath.PluginModuleRegistry;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class DynamicModulesClassPathProvider implements ClassPathProvider {
    private final ModuleRegistry moduleRegistry;
    private final PluginModuleRegistry pluginModuleRegistry;

    public DynamicModulesClassPathProvider(ModuleRegistry moduleRegistry, PluginModuleRegistry pluginModuleRegistry) {
        this.moduleRegistry = moduleRegistry;
        this.pluginModuleRegistry = pluginModuleRegistry;
    }

    public Set<File> findClassPath(String name) {
        if (name.equals("GRADLE_PLUGINS")) {
            Set<File> classpath = new LinkedHashSet<File>();
            for (Module pluginModule : pluginModuleRegistry.getPluginModules()) {
                classpath.addAll(pluginModule.getClasspath());
            }
            return classpath;
        }
        if (name.equals("GRADLE_CORE_IMPL")) {
            return moduleRegistry.getModule("gradle-core-impl").getClasspath();
        }

        return null;
    }
}
