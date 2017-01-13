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
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;

import java.util.Arrays;
import java.util.Set;

public class DynamicModulesClassPathProvider implements ClassPathProvider {
    private final ModuleRegistry moduleRegistry;
    private final PluginModuleRegistry pluginModuleRegistry;

    public DynamicModulesClassPathProvider(ModuleRegistry moduleRegistry, PluginModuleRegistry pluginModuleRegistry) {
        this.moduleRegistry = moduleRegistry;
        this.pluginModuleRegistry = pluginModuleRegistry;
    }

    public ClassPath findClassPath(String name) {
        if (name.equals("GRADLE_EXTENSIONS")) {
            Set<Module> coreModules = moduleRegistry.getModule("gradle-core").getAllRequiredModules();
            ClassPath classpath = new DefaultClassPath();
            for (String moduleName : Arrays.asList("gradle-workers", "gradle-dependency-management", "gradle-plugin-use")) {
                for (Module module : moduleRegistry.getModule(moduleName).getAllRequiredModules()) {
                    if (!coreModules.contains(module)) {
                        classpath = classpath.plus(module.getClasspath());
                    }
                }
            }
            for (Module pluginModule : pluginModuleRegistry.getPluginModules()) {
                classpath = classpath.plus(pluginModule.getClasspath());
            }
            return classpath;
        }

        return null;
    }
}
