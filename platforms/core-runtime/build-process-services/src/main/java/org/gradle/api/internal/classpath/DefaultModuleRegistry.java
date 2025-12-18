/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.internal.classpath;

import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.installation.GradleInstallation;
import org.gradle.util.internal.GUtil;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Determines the classpath for a module by looking for a '${module}-classpath.properties' resource with 'name' set to the name of the module.
 */
@NullMarked
public class DefaultModuleRegistry implements ModuleRegistry {

    private final GradleInstallation gradleInstallation;
    private final Map<String, Optional<Module>> modules = new ConcurrentHashMap<>();

    public DefaultModuleRegistry(@Nullable GradleInstallation gradleInstallation) {
        if (gradleInstallation == null) {
            throw new IllegalArgumentException("A Gradle installation is required to execute Gradle.");
        }

        this.gradleInstallation = gradleInstallation;
    }

    @Override
    @Nullable
    public Module findModule(String moduleName) {
        return modules.computeIfAbsent(moduleName, n -> {
            File propertiesFile = findPropertiesFile(moduleName);
            if (propertiesFile != null) {
                return Optional.of(createModule(moduleName, propertiesFile));
            } else {
                return Optional.empty();
            }
        }).orElse(null);
    }

    @Override
    public Module getModule(String name) throws UnknownModuleException {
        Module module = findModule(name);
        if (module != null) {
            return module;
        }
        throw new UnknownModuleException(String.format("Cannot find module '%s' in distribution directory '%s'.", name, gradleInstallation.getGradleHome()));
    }

    private @Nullable File findPropertiesFile(String name) {
        for (File libDir : gradleInstallation.getLibDirs()) {
            File propertiesFile = new File(libDir, name + ".properties");
            if (propertiesFile.isFile()) {
                return propertiesFile;
            }
        }

        return null;
    }

    private DefaultModule createModule(String moduleName, File propertiesFile) {
        Properties properties = GUtil.loadProperties(propertiesFile);
        List<String> dependencies = split(properties.getProperty("dependencies"));
        String jarFileName = properties.getProperty("jarFile");
        if (jarFileName == null) {
            throw new IllegalArgumentException("Missing required property 'jarFile' in module properties file ' " + propertiesFile.getAbsolutePath() + "'.");
        }

        String group = properties.getProperty("alias.group");
        String name = properties.getProperty("alias.name");
        String version = properties.getProperty("alias.version");
        Module.ModuleAlias alias = null;
        if (group != null && name != null && version != null) {
            alias = new DefaultModuleAlias(group, name, version);
        }
        File jarFile = new File(propertiesFile.getParent(), jarFileName);
        if (!jarFile.exists()) {
            throw new IllegalArgumentException(String.format("Cannot find JAR '%s' required by module '%s' using classpath or distribution directory '%s'", name, moduleName, gradleInstallation.getGradleHome()));
        }
        return new DefaultModule(moduleName, jarFile, dependencies, alias);
    }

    private static List<String> split(@Nullable String value) {
        if (value == null) {
            return Collections.emptyList();
        }
        value = value.trim();
        if (value.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(value.split(","));
    }

    private static class DefaultModule implements Module {

        private final String name;
        private final List<String> projects;
        private final ClassPath implementationClasspath;
        private final Module.@Nullable ModuleAlias alias;

        public DefaultModule(
            String name,
            File implementationClasspath,
            List<String> projects,
            @Nullable ModuleAlias alias
        ) {
            this.name = name;
            this.projects = projects;
            this.implementationClasspath = DefaultClassPath.of(implementationClasspath);
            this.alias = alias;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<String> getDependencyNames() {
            return projects;
        }

        @Override
        public String toString() {
            return "module '" + name + "'";
        }

        @Override
        public ClassPath getImplementationClasspath() {
            return implementationClasspath;
        }

        @Override
        public @Nullable ModuleAlias getAlias() {
            return alias;
        }
    }

    public static class DefaultModuleAlias implements Module.ModuleAlias {

        private final String group;
        private final String name;
        private final String version;

        private DefaultModuleAlias(String group, String name, String version) {
            this.group = group;
            this.name = name;
            this.version = version;
        }

        @Override
        public String getGroup() {
            return group;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getVersion() {
            return version;
        }

    }

}
