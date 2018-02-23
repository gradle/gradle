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
package org.gradle.api.internal.classpath;

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.installation.GradleInstallation;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Determines the classpath for a module by looking for a '${module}-classpath.properties' resource with 'name' set to the name of the module.
 */
public class DefaultModuleRegistry implements ModuleRegistry {
    private final GradleInstallation gradleInstallation;
    private final Map<String, Module> modules = new HashMap<String, Module>();
    private final List<File> classpath = new ArrayList<File>();
    private final Map<String, File> classpathJars = new LinkedHashMap<String, File>();

    public DefaultModuleRegistry(@Nullable GradleInstallation gradleInstallation) {
        this(ClassPath.EMPTY, gradleInstallation);
    }

    public DefaultModuleRegistry(ClassPath additionalModuleClassPath, @Nullable GradleInstallation gradleInstallation) {
        this(DefaultModuleRegistry.class.getClassLoader(), additionalModuleClassPath, gradleInstallation);
    }

    private DefaultModuleRegistry(ClassLoader classLoader, ClassPath additionalModuleClassPath, @Nullable GradleInstallation gradleInstallation) {
        this.gradleInstallation = gradleInstallation;

        for (File classpathFile : new EffectiveClassPath(classLoader).plus(additionalModuleClassPath).getAsFiles()) {
            classpath.add(classpathFile);
            if (classpathFile.isFile() && !classpathJars.containsKey(classpathFile.getName())) {
                classpathJars.put(classpathFile.getName(), classpathFile);
            }
        }
    }

    @Override
    public ClassPath getAdditionalClassPath() {
        return gradleInstallation == null ? new DefaultClassPath(classpath) : ClassPath.EMPTY;
    }

    public Module getExternalModule(String name) {
        File externalJar = findJar(name);
        if (externalJar == null) {
            throw new UnknownModuleException(String.format("Cannot locate JAR for module '%s' in distribution directory '%s'.", name, gradleInstallation.getGradleHome()));
        }
        return new DefaultModule(name, Collections.singleton(externalJar), Collections.<File>emptySet());
    }

    public Module getModule(String name) {
        Module module = modules.get(name);
        if (module == null) {
            module = loadModule(name);
            modules.put(name, module);
        }
        return module;
    }

    @Override
    @Nullable
    public Module findModule(String name) {
        Module module = modules.get(name);
        if (module == null) {
            module = loadOptionalModule(name);
            if (module != null) {
                modules.put(name, module);
            }
        }
        return module;
    }

    private Module loadModule(String moduleName) {
        Module module = loadOptionalModule(moduleName);
        if (module != null) {
            return module;
        }
        if (gradleInstallation == null) {
            throw new UnknownModuleException(String.format("Cannot locate manifest for module '%s' in classpath: %s.", moduleName, classpath));
        }
        throw new UnknownModuleException(String.format("Cannot locate JAR for module '%s' in distribution directory '%s'.", moduleName, gradleInstallation.getGradleHome()));
    }

    private Module loadOptionalModule(String moduleName) {
        File jarFile = findJar(moduleName);
        if (jarFile != null) {
            Set<File> implementationClasspath = new LinkedHashSet<File>();
            implementationClasspath.add(jarFile);
            Properties properties = loadModuleProperties(moduleName, jarFile);
            return module(moduleName, properties, implementationClasspath);
        }

        String resourceName = moduleName + "-classpath.properties";
        Set<File> implementationClasspath = new LinkedHashSet<File>();
        findImplementationClasspath(moduleName, implementationClasspath);
        for (File file : implementationClasspath) {
            if (file.isDirectory()) {
                File propertiesFile = new File(file, resourceName);
                if (propertiesFile.isFile()) {
                    Properties properties = GUtil.loadProperties(propertiesFile);
                    return module(moduleName, properties, implementationClasspath);
                }
            }
        }
        return null;
    }

    private Module module(String moduleName, Properties properties, Set<File> implementationClasspath) {
        String[] runtimeJarNames = split(properties.getProperty("runtime"));
        Set<File> runtimeClasspath = findDependencyJars(moduleName, runtimeJarNames);

        String[] projects = split(properties.getProperty("projects"));
        String[] optionalProjects = split(properties.getProperty("optional"));
        return new DefaultModule(moduleName, implementationClasspath, runtimeClasspath, projects, optionalProjects);
    }

    private Set<File> findDependencyJars(String moduleName, String[] jarNames) {
        Set<File> runtimeClasspath = new LinkedHashSet<File>();
        for (String jarName : jarNames) {
            runtimeClasspath.add(findDependencyJar(moduleName, jarName));
        }
        return runtimeClasspath;
    }

    private Set<Module> getModules(String[] projectNames) {
        Set<Module> modules = new LinkedHashSet<Module>();
        for (String project : projectNames) {
            modules.add(getModule(project));
        }
        return modules;
    }

    private String[] split(String value) {
        if (value == null) {
            return new String[0];
        }
        value = value.trim();
        if (value.length() == 0) {
            return new String[0];
        }
        return value.split(",");
    }

    private void findImplementationClasspath(String name, Collection<File> implementationClasspath) {
        List<String> suffixes = new ArrayList<String>();
        Matcher matcher = Pattern.compile("gradle-(.+)").matcher(name);
        matcher.matches();
        String projectDirName = matcher.group(1);
        String projectName = toCamelCase(projectDirName);
        suffixes.add(("/out/production/" + projectName).replace('/', File.separatorChar));
        suffixes.add(("/" + projectDirName + "/bin").replace('/', File.separatorChar));
        suffixes.add(("/" + projectDirName + "/src/main/resources").replace('/', File.separatorChar));
        suffixes.add(("/" + projectDirName + "/build/classes/main").replace('/', File.separatorChar));
        suffixes.add(("/" + projectDirName + "/build/resources/main").replace('/', File.separatorChar));
        suffixes.add(("/" + projectDirName + "/build/generated-resources/main").replace('/', File.separatorChar));
        suffixes.add(("/" + projectDirName + "/build/generated-resources/test").replace('/', File.separatorChar));
        for (File file : classpath) {
            if (file.isDirectory()) {
                for (String suffix : suffixes) {
                    if (file.getAbsolutePath().endsWith(suffix)) {
                        implementationClasspath.add(file);
                    }
                }
            }
        }
    }

    private String toCamelCase(String name) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = Pattern.compile("-([^-])").matcher(name);
        while (matcher.find()) {
            matcher.appendReplacement(result, "");
            result.append(matcher.group(1).toUpperCase());
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Properties loadModuleProperties(String name, File jarFile) {
        try {
            ZipFile zipFile = new ZipFile(jarFile);
            try {
                final String entryName = name + "-classpath.properties";
                ZipEntry entry = zipFile.getEntry(entryName);
                if (entry == null) {
                    throw new IllegalStateException("Did not find " + entryName + " in " + jarFile.getAbsolutePath());
                }
                return GUtil.loadProperties(zipFile.getInputStream(entry));
            } finally {
                zipFile.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not load properties for module '%s' from %s", name, jarFile), e);
        }
    }

    private File findJar(String name) {
        Pattern pattern = Pattern.compile(Pattern.quote(name) + "-\\d.+\\.jar");
        if (gradleInstallation != null) {
            for (File libDir : gradleInstallation.getLibDirs()) {
                for (File file : libDir.listFiles()) {
                    if (pattern.matcher(file.getName()).matches()) {
                        return file;
                    }
                }
            }
        }
        for (File file : classpath) {
            if (pattern.matcher(file.getName()).matches()) {
                return file;
            }
        }
        return null;
    }

    private File findDependencyJar(String module, String name) {
        File jarFile = classpathJars.get(name);
        if (jarFile != null) {
            return jarFile;
        }
        if (gradleInstallation == null) {
            throw new IllegalArgumentException(String.format("Cannot find JAR '%s' required by module '%s' using classpath.", name, module));
        }
        for (File libDir : gradleInstallation.getLibDirs()) {
            jarFile = new File(libDir, name);
            if (jarFile.isFile()) {
                return jarFile;
            }
        }
        throw new IllegalArgumentException(String.format("Cannot find JAR '%s' required by module '%s' using classpath or distribution directory '%s'", name, module, gradleInstallation.getGradleHome()));
    }

    private class DefaultModule implements Module {

        private final String name;
        private final String[] projects;
        private final String[] optionalProjects;
        private final ClassPath implementationClasspath;
        private final ClassPath runtimeClasspath;
        private final ClassPath classpath;

        public DefaultModule(String name, Set<File> implementationClasspath, Set<File> runtimeClasspath, String[] projects, String[] optionalProjects) {
            this.name = name;
            this.projects = projects;
            this.optionalProjects = optionalProjects;
            this.implementationClasspath = new DefaultClassPath(implementationClasspath);
            this.runtimeClasspath = new DefaultClassPath(runtimeClasspath);
            Set<File> classpath = new LinkedHashSet<File>();
            classpath.addAll(implementationClasspath);
            classpath.addAll(runtimeClasspath);
            this.classpath = new DefaultClassPath(classpath);
        }

        public DefaultModule(String name, Set<File> singleton, Set<File> files) {
            this(name, singleton, files, NO_PROJECTS, NO_PROJECTS);
        }

        @Override
        public String toString() {
            return "module '" + name + "'";
        }

        public Set<Module> getRequiredModules() {
            return getModules(projects);
        }

        public ClassPath getImplementationClasspath() {
            return implementationClasspath;
        }

        public ClassPath getRuntimeClasspath() {
            return runtimeClasspath;
        }

        public ClassPath getClasspath() {
            return classpath;
        }

        public Set<Module> getAllRequiredModules() {
            Set<Module> modules = new LinkedHashSet<Module>();
            collectRequiredModules(modules);
            return modules;
        }

        private void collectRequiredModules(Set<Module> modules) {
            if (!modules.add(this)) {
                return;
            }
            for (Module module : getRequiredModules()) {
                collectDependenciesOf(module, modules);
            }
            for (String optionalProject : optionalProjects) {
                Module module = findModule(optionalProject);
                if (module != null) {
                    collectDependenciesOf(module, modules);
                }
            }
        }

        private void collectDependenciesOf(Module module, Set<Module> modules) {
            ((DefaultModule) module).collectRequiredModules(modules);
        }

        private Module findModule(String optionalProject) {
            try {
                return getModule(optionalProject);
            } catch (UnknownModuleException ex) {
                return null;
            }
        }
    }

    private static final String[] NO_PROJECTS = new String[0];
}
