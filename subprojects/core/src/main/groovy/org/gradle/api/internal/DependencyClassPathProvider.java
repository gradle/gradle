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

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory.ClassPathNotation.GRADLE_API;
import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory.ClassPathNotation.LOCAL_GROOVY;

public class DependencyClassPathProvider extends AbstractClassPathProvider {
    private final ModuleRegistry moduleRegistry;

    public DependencyClassPathProvider(ModuleRegistry moduleRegistry) {
        this.moduleRegistry = moduleRegistry;
        add(LOCAL_GROOVY.name(), toPatterns("groovy-all"));
    }

    @Override
    public Set<File> findClassPath(String name) {
        if (name.equals(GRADLE_API.name())) {
            Set<File> classpath = new LinkedHashSet<File>();
            getRuntimeClasspath("gradle-core", classpath);
            getRuntimeClasspath("gradle-core-impl", classpath);
            getRuntimeClasspath("gradle-plugins", classpath);
            return classpath;
        }

        return super.findClassPath(name);
    }

    private void getRuntimeClasspath(String projectName, Collection<File> classpath) {
        Module module = moduleRegistry.getModule(projectName);
        classpath.addAll(module.getImplementationClasspath());
        classpath.addAll(module.getRuntimeClasspath());
    }
}
