/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;

public class DefaultClassPathProvider implements ClassPathProvider {
    private final ModuleRegistry moduleRegistry;

    public DefaultClassPathProvider(ModuleRegistry moduleRegistry) {
        this.moduleRegistry = moduleRegistry;
    }

    public ClassPath findClassPath(String name) {
        if (name.equals("GRADLE_RUNTIME")) {
            ClassPath classpath = new DefaultClassPath();
            for (Module module : moduleRegistry.getModule("gradle-launcher").getAllRequiredModules()) {
                classpath = classpath.plus(module.getClasspath());
            }
            return classpath;
        }
        if (name.equals("GRADLE_CORE")) {
            return moduleRegistry.getModule("gradle-core").getImplementationClasspath();
        }
        if (name.equals("GRADLE_BASE_SERVICES")) {
            return moduleRegistry.getModule("gradle-base-services").getImplementationClasspath();
        }
        if (name.equals("COMMONS_CLI")) {
            return moduleRegistry.getExternalModule("commons-cli").getClasspath();
        }
        if (name.equals("ANT")) {
            ClassPath classpath = new DefaultClassPath();
            classpath = classpath.plus(moduleRegistry.getExternalModule("ant").getClasspath());
            classpath = classpath.plus(moduleRegistry.getExternalModule("ant-launcher").getClasspath());
            return classpath;
        }
        if (name.equals("GROOVY")) {
            return moduleRegistry.getExternalModule("groovy-all").getClasspath();
        }

        return null;
    }
}
