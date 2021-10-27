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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.classpath.Module;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.classpath.PluginModuleRegistry;
import org.gradle.internal.classpath.ClassPath;

import java.util.Arrays;
import java.util.Set;

import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory.ClassPathNotation.GRADLE_API;
import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory.ClassPathNotation.GRADLE_KOTLIN_DSL;
import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory.ClassPathNotation.GRADLE_TEST_KIT;
import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory.ClassPathNotation.LOCAL_GROOVY;

public class DependencyClassPathProvider implements ClassPathProvider {
    private final ModuleRegistry moduleRegistry;
    private final PluginModuleRegistry pluginModuleRegistry;

    private ClassPath gradleApi;

    public DependencyClassPathProvider(ModuleRegistry moduleRegistry, PluginModuleRegistry pluginModuleRegistry) {
        this.moduleRegistry = moduleRegistry;
        this.pluginModuleRegistry = pluginModuleRegistry;
    }

    @Override
    public ClassPath findClassPath(String name) {
        if (name.equals(GRADLE_API.name())) {
            return gradleApi();
        }
        if (name.equals(GRADLE_TEST_KIT.name())) {
            return gradleTestKit();
        }
        if (name.equals(LOCAL_GROOVY.name())) {
            return localGroovy();
        }
        if (name.equals(GRADLE_KOTLIN_DSL.name())) {
            return gradleKotlinDsl();
        }
        return null;
    }

    private ClassPath gradleApi() {
        if (gradleApi == null) {
            gradleApi = initGradleApi();
        }
        return gradleApi;
    }

    private ClassPath initGradleApi() {
        // This gradleApi() method creates a Gradle API classpath based on real jars for embedded test running.
        // Currently this leaks additional dependencies that may cause unexpected issues.
        // This method is NOT involved in generating the gradleApi() Jar which is used in a real Gradle run.
        ClassPath classpath = ClassPath.EMPTY;
        for (String moduleName : Arrays.asList("gradle-worker-processes", "gradle-launcher", "gradle-workers", "gradle-dependency-management", "gradle-plugin-use", "gradle-tooling-api", "gradle-configuration-cache")) {
            classpath = classpath.plus(moduleRegistry.getModule(moduleName).getAllRequiredModulesClasspath());
        }
        for (Module pluginModule : pluginModuleRegistry.getApiModules()) {
            classpath = classpath.plus(pluginModule.getClasspath());
        }
        return classpath.removeIf(f ->
            // Remove dependencies that are not part of the API and cause trouble when they leak.
            // 'kotlin-sam-with-receiver-compiler-plugin' clashes with 'kotlin-sam-with-receiver' causing a 'SamWithReceiverComponentRegistrar is not compatible with this version of compiler' exception
            f.getName().startsWith("kotlin-sam-with-receiver-compiler-plugin")
        );
    }

    private ClassPath gradleTestKit() {
        return moduleRegistry.getModule("gradle-test-kit").getClasspath();
    }

    private ClassPath localGroovy() {
        Set<String> groovyModules = ImmutableSet.of(
            "groovy-ant",
            "groovy-astbuilder",
            "groovy-console",
            "groovy-datetime",
            "groovy-dateutil",
            "groovy-groovydoc",
            "groovy-json",
            "groovy-nio",
            "groovy-sql",
            "groovy-templates",
            "groovy-test",
            "groovy-xml",
            "javaparser-core");
        ClassPath groovy = moduleRegistry.getExternalModule("groovy").getClasspath();
        for (String groovyModule : groovyModules) {
            groovy = groovy.plus(moduleRegistry.getExternalModule(groovyModule).getClasspath());
        }
        return groovy;
    }

    private ClassPath gradleKotlinDsl() {
        return moduleRegistry.getModule("gradle-kotlin-dsl").getAllRequiredModulesClasspath();
    }
}
