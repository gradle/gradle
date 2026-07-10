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
 * Responsible for loading {@link Module modules} from a {@link GradleInstallation}.
 * <p>
 * A gradle installation contains a number of directories, each of which may contain modules.
 * Modules are defined by a properties file, where the properties file is named
 * {@code moduleName.properties}. The layout of each module directory is Gradle version
 * independent. Different Gradle versions should not attempt to assume the structure of
 * another version's module registry.
 * <p>
 * Each module is implemented by a single jar. The name of the jar is included in the module
 * properties file, and the jar is expected to live next to the properties file.
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

    /**
     * Find the properties file corresponding to the module with the given name.
     */
    private @Nullable File findPropertiesFile(String name) {
        for (File libDir : gradleInstallation.getLibDirs()) {
            File propertiesFile = new File(libDir, name + ".properties");
            if (propertiesFile.isFile()) {
                return propertiesFile;
            }
        }

        return null;
    }

    /**
     * Given a properties file, load the module it describes into a {@link Module} instance.
     *
     * @throws RuntimeException If properties file is invalid or describes an invalid module.
     */
    private static DefaultModule createModule(String moduleName, File propertiesFile) {
        Properties properties = GUtil.loadProperties(propertiesFile);

        List<String> dependencies = split(getProperty("dependencies", properties, propertiesFile));

        // Some modules have no implementation jar, like platforms/BOMs or those derived
        // from components which use external "available-at" variants, like kotlinx.
        ClassPath classpath = ClassPath.EMPTY;
        String jarFileName = properties.getProperty("jarFile");
        if (jarFileName != null) {
            File jarFile = new File(propertiesFile.getParent(), jarFileName);
            if (!jarFile.exists()) {
                throw new IllegalArgumentException(String.format("Cannot find JAR '%s' required by module '%s' distribution directory.", jarFileName, moduleName));
            }
            classpath = DefaultClassPath.of(jarFile);
        }

        String group = properties.getProperty("alias.group");
        String name = properties.getProperty("alias.name");
        String version = properties.getProperty("alias.version");
        Module.ModuleAlias alias = null;
        if (group != null && name != null && version != null) {
            alias = new DefaultModuleAlias(group, name, version);
        } else if (group != null || name != null || version != null) {
            throw new IllegalArgumentException(String.format("Cannot create module '%s' with partial module alias.", moduleName));
        }

        return new DefaultModule(moduleName, classpath, dependencies, alias);
    }

    private static String getProperty(String propertyName, Properties properties, File propertiesFile) {
        String value = properties.getProperty(propertyName);
        if (value == null) {
            throw new IllegalArgumentException(String.format(
                "Missing required property '%s' in module properties file '%s'.",
                propertyName,
                propertiesFile.getAbsolutePath()
            ));
        }
        return value;
    }

    private static List<String> split(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(trimmed.split(","));
    }

    private static class DefaultModule implements Module {

        private final String name;
        private final List<String> dependencyNames;
        private final ClassPath implementationClasspath;
        private final @Nullable ModuleAlias alias;

        public DefaultModule(
            String name,
            ClassPath implementationClasspath,
            List<String> dependencyNames,
            @Nullable ModuleAlias alias
        ) {
            this.name = name;
            this.implementationClasspath = implementationClasspath;
            this.dependencyNames = dependencyNames;
            this.alias = alias;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ClassPath getImplementationClasspath() {
            return implementationClasspath;
        }

        @Override
        public List<String> getDependencyNames() {
            return dependencyNames;
        }

        @Override
        public @Nullable ModuleAlias getAlias() {
            return alias;
        }

        @Override
        public String toString() {
            return "module '" + name + "'";
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
