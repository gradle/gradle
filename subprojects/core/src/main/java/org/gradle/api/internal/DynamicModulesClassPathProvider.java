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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.classpath.Module;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.classpath.PluginModuleRegistry;
import org.gradle.api.specs.Spec;
import org.gradle.internal.classpath.ClassPath;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DynamicModulesClassPathProvider implements ClassPathProvider {
    private final ModuleRegistry moduleRegistry;
    private final PluginModuleRegistry pluginModuleRegistry;
    private final JavaVersion javaVersion;

    public DynamicModulesClassPathProvider(ModuleRegistry moduleRegistry, PluginModuleRegistry pluginModuleRegistry) {
        this(moduleRegistry, pluginModuleRegistry, JavaVersion.current());
    }

    @VisibleForTesting
    protected DynamicModulesClassPathProvider(ModuleRegistry moduleRegistry, PluginModuleRegistry pluginModuleRegistry, JavaVersion javaVersion) {
        this.moduleRegistry = moduleRegistry;
        this.pluginModuleRegistry = pluginModuleRegistry;
        this.javaVersion = javaVersion;
    }

    @Override
    public @Nullable ClassPath findClassPath(String name) {
        if (name.equals("GRADLE_EXTENSIONS")) {
            return gradleExtensionsWithout(ImmutableList.of("gradle-core"));
        }

        if (name.equals("GRADLE_WORKER_EXTENSIONS")) {
            return gradleExtensionsWithout(ImmutableList.of("gradle-core", "gradle-workers", "gradle-dependency-management"));
        }

        return null;
    }

    private ClassPath gradleExtensionsWithout(Iterable<String> modulesToExclude) {
        Set<Module> coreModules = ImmutableSet.copyOf(allRequiredModulesOf(modulesToExclude));
        ClassPath classpath = ClassPath.EMPTY;

        List<Module> extensionModules = allRequiredModulesOf(GRADLE_EXTENSION_MODULES);
        classpath = plusExtensionModules(classpath, extensionModules, coreModules);

        List<Module> optionalExtensionModules = allRequiredModulesOfOptional(GRADLE_OPTIONAL_EXTENSION_MODULES);
        classpath = plusExtensionModules(classpath, optionalExtensionModules, coreModules);

        classpath = classpath.plus(moduleRegistry.getRuntimeClasspath(
            Iterables.concat(
                pluginModuleRegistry.getApiModules(),
                pluginModuleRegistry.getImplementationModules()
            )
        ));
        return removeJaxbIfIncludedInCurrentJdk(classpath);
    }

    private ClassPath removeJaxbIfIncludedInCurrentJdk(ClassPath classpath) {
        if (!javaVersion.isJava9Compatible()) {
            return classpath.removeIf(new Spec<File>() {
                @Override
                public boolean isSatisfiedBy(File file) {
                    return file.getName().startsWith("jaxb-impl-");
                }
            });
        }
        return classpath;
    }

    private List<Module> allRequiredModulesOf(Iterable<String> names) {
        Iterable<Module> modules = Iterables.transform(names, moduleRegistry::getModule);
        return moduleRegistry.getRuntimeModules(modules);
    }

    private List<Module> allRequiredModulesOfOptional(Collection<String> names) {
        List<Module> modules = new ArrayList<>(names.size());
        for (String name : names) {
            Module optionalModule = moduleRegistry.findModule(name);
            if (optionalModule != null) {
                modules.add(optionalModule);
            }
        }
        return moduleRegistry.getRuntimeModules(modules);
    }

    private static ClassPath plusExtensionModules(ClassPath classpath, List<Module> extensionModules, Set<Module> coreModules) {
        for (Module module : extensionModules) {
            if (!coreModules.contains(module)) {
                classpath = classpath.plus(module.getImplementationClasspath());
            }
        }
        return classpath;
    }

    private static final ImmutableList<String> GRADLE_EXTENSION_MODULES = ImmutableList.of(
        "gradle-workers",
        "gradle-dependency-management",
        "gradle-software-diagnostics",
        "gradle-plugin-use",
        "gradle-instrumentation-declarations"
    );

    private static final ImmutableList<String> GRADLE_OPTIONAL_EXTENSION_MODULES = ImmutableList.of(
        "gradle-daemon-services",
        "gradle-kotlin-dsl-provider-plugins",
        "gradle-kotlin-dsl-tooling-builders"
    );
}
