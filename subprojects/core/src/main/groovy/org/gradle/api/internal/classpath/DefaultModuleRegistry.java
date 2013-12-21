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
import org.gradle.api.internal.GradleDistributionLocator;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.util.GUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
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
public class DefaultModuleRegistry implements ModuleRegistry, GradleDistributionLocator {
    private final ClassLoader classLoader;
    private final File distDir;
    private final Map<String, Module> modules = new HashMap<String, Module>();
    private final List<File> classpath = new ArrayList<File>();
    private final Map<String, File> classpathJars = new LinkedHashMap<String, File>();
    private final List<File> libDirs = new ArrayList<File>();

    public DefaultModuleRegistry() {
        this(DefaultModuleRegistry.class.getClassLoader(), findDistDir());
    }

    DefaultModuleRegistry(ClassLoader classLoader, File distDir) {
        this.classLoader = classLoader;
        this.distDir = distDir;
        for (File classpathFile : new EffectiveClassPath(classLoader).getAsFiles()) {
            classpath.add(classpathFile);
            if (classpathFile.isFile() && !classpathJars.containsKey(classpathFile.getName())) {
                classpathJars.put(classpathFile.getName(), classpathFile);
            }
        }

        if (distDir != null) {
            libDirs.add(new File(distDir, "lib"));
            libDirs.add(new File(distDir, "lib/plugins"));
        }
    }

    private static File findDistDir() {
        File codeSource = ClasspathUtil.getClasspathForClass(DefaultModuleRegistry.class);
        if (codeSource.isFile()) {
            // Loaded from a JAR - let's see if its in the lib directory, and there's a lib/plugins directory
            File libDir = codeSource.getParentFile();
            if (!libDir.getName().equals("lib") || !new File(libDir, "plugins").isDirectory()) {
                return null;
            }
            return libDir.getParentFile();
        } else {
            // Loaded from a classes dir - assume we're running from the ide or tests
            return null;
        }
    }

    /**
     * Returns all the candidate JARs to be considered by this registry.
     */
    public Set<File> getFullClasspath() {
        return new LinkedHashSet<File>(classpath);
    }

    public File getGradleHome() {
        return distDir;
    }

    public Module getExternalModule(String name) {
        File externalJar = findExternalJar(name);
        return new DefaultModule(name, Collections.singleton(externalJar), Collections.<File>emptySet(), Collections.<Module>emptySet());
    }

    public Module getModule(String name) {
        Module module = modules.get(name);
        if (module == null) {
            module = loadModule(name);
            modules.put(name, module);
        }
        return module;
    }

    private Module loadModule(String moduleName) {
        File jarFile = findModuleJar(moduleName);
        if (jarFile != null) {
            Set<File> implementationClasspath = new LinkedHashSet<File>();
            implementationClasspath.add(jarFile);
            Properties properties = loadModuleProperties(moduleName, jarFile);
            return module(moduleName, properties, implementationClasspath);
        }

        String resourceName = String.format("%s-classpath.properties", moduleName);
        URL propertiesUrl = classLoader.getResource(resourceName);
        if (propertiesUrl != null) {
            Set<File> implementationClasspath = new LinkedHashSet<File>();
            findImplementationClasspath(moduleName, implementationClasspath);
            implementationClasspath.add(ClasspathUtil.getClasspathForResource(propertiesUrl, resourceName));
            Properties properties = GUtil.loadProperties(propertiesUrl);
            return module(moduleName, properties, implementationClasspath);
        }

        if (distDir == null) {
            throw new UnknownModuleException(String.format("Cannot locate classpath manifest for module '%s' in classpath.", moduleName));
        }
        throw new UnknownModuleException(String.format("Cannot locate JAR for module '%s' in distribution directory '%s'.", moduleName, distDir));
    }

    private Module module(String moduleName, Properties properties, Set<File> implementationClasspath) {
        Set<File> runtimeClasspath = new LinkedHashSet<File>();
        String runtime = properties.getProperty("runtime");
        for (String jarName : split(runtime)) {
            runtimeClasspath.add(findDependencyJar(moduleName, jarName));
        }

        Set<Module> modules = new LinkedHashSet<Module>();
        String projects = properties.getProperty("projects");
        for (String project : split(projects)) {
            modules.add(getModule(project));
        }

        return new DefaultModule(moduleName, implementationClasspath, runtimeClasspath, modules);
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
        suffixes.add(String.format("/out/production/%s", projectName).replace('/', File.separatorChar));
        suffixes.add(String.format("/%s/bin", projectDirName).replace('/', File.separatorChar));
        suffixes.add(String.format("/%s/src/main/resources", projectDirName).replace('/', File.separatorChar));
        suffixes.add(String.format("/%s/build/classes/main", projectDirName).replace('/', File.separatorChar));
        suffixes.add(String.format("/%s/build/resources/main", projectDirName).replace('/', File.separatorChar));
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
                ZipEntry entry = zipFile.getEntry(String.format("%s-classpath.properties", name));
                return GUtil.loadProperties(zipFile.getInputStream(entry));
            } finally {
                zipFile.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private File findModuleJar(String name) {
        Pattern pattern = Pattern.compile(Pattern.quote(name) + "-\\d.+\\.jar");
        for (File libDir : libDirs) {
            for (File file : libDir.listFiles()) {
                if (pattern.matcher(file.getName()).matches()) {
                    return file;
                }
            }
        }
        return null;
    }

    private File findExternalJar(String name) {
        Pattern pattern = Pattern.compile(Pattern.quote(name) + "-\\d.+\\.jar");
        for (File file : classpath) {
            if (pattern.matcher(file.getName()).matches()) {
                return file;
            }
        }
        for (File libDir : libDirs) {
            for (File file : libDir.listFiles()) {
                if (pattern.matcher(file.getName()).matches()) {
                    return file;
                }
            }
        }
        throw new UnknownModuleException(String.format("Cannot locate JAR for module '%s' in distribution directory '%s'.", name, distDir));
    }

    private File findDependencyJar(String module, String name) {
        File jarFile = classpathJars.get(name);
        if (jarFile != null) {
            return jarFile;
        }
        if (distDir == null) {
            throw new IllegalArgumentException(String.format("Cannot find JAR '%s' required by module '%s' using classpath.", name, module));
        }
        for (File libDir : libDirs) {
            jarFile = new File(libDir, name);
            if (jarFile.isFile()) {
                return jarFile;
            }
        }
        throw new IllegalArgumentException(String.format("Cannot find JAR '%s' required by module '%s' using classpath or distribution directory '%s'", name, module, distDir));
    }

    private static class DefaultModule implements Module {
        private final String name;
        private final ClassPath implementationClasspath;
        private final ClassPath runtimeClasspath;
        private final Set<Module> modules;
        private final ClassPath classpath;

        public DefaultModule(String name, Set<File> implementationClasspath, Set<File> runtimeClasspath, Set<Module> modules) {
            this.name = name;
            this.implementationClasspath = new DefaultClassPath(implementationClasspath);
            this.runtimeClasspath = new DefaultClassPath(runtimeClasspath);
            this.modules = modules;
            Set<File> classpath = new LinkedHashSet<File>();
            classpath.addAll(implementationClasspath);
            classpath.addAll(runtimeClasspath);
            this.classpath = new DefaultClassPath(classpath);
        }

        @Override
        public String toString() {
            return String.format("module '%s'", name);
        }

        public Set<Module> getRequiredModules() {
            return modules;
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
            modules.add(this);
            for (Module module : this.modules) {
                modules.addAll(module.getAllRequiredModules());
            }
            return modules;
        }
    }
}
