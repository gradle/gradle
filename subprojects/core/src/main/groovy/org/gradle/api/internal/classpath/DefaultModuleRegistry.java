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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.GradleDistributionLocator;
import org.gradle.util.ClasspathUtil;
import org.gradle.util.GUtil;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
        for (URL url : ClasspathUtil.getClasspath(classLoader)) {
            if (url.getProtocol().equals("file")) {
                try {
                    File entry = new File(url.toURI());
                    classpath.add(entry);
                    if (entry.isFile()) {
                        classpathJars.put(entry.getName(), entry);
                    }
                } catch (URISyntaxException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        if (distDir != null) {
            libDirs.add(new File(distDir, "lib"));
            libDirs.add(new File(distDir, "lib/plugins"));
            libDirs.add(new File(distDir, "lib/core-impl"));
        }
    }

    private static File findDistDir() {
        File codeSource = ClasspathUtil.getClasspathForClass(DefaultModuleRegistry.class);
        if (codeSource.isFile()) {
            // Loaded from a JAR - assume we're running from the distribution
            return codeSource.getParentFile().getParentFile();
        } else {
            // Loaded from a classes dir - assume we're running from the ide or tests
            return null;
        }
    }

    public File getGradleHome() {
        return distDir;
    }

    public Module getModule(String name) {
        Module module = modules.get(name);
        if (module == null) {
            module = loadModule(name);
            modules.put(name, module);
        }
        return module;
    }

    private Module loadModule(String name) {
        List<File> implementationClasspath = new ArrayList<File>();

        String resource = String.format("%s-classpath.properties", name);
        Properties properties;
        URL url = classLoader.getResource(resource);
        if (url != null) {
            properties = GUtil.loadProperties(url);
            findImplementationClasspath(name, implementationClasspath);
            implementationClasspath.add(ClasspathUtil.getClasspathForResource(classLoader, resource));
        } else if (distDir != null) {
            File jarFile = findModuleJar(name);
            implementationClasspath.add(jarFile);
            properties = loadModuleProperties(jarFile, resource);
        } else {
            throw new IllegalArgumentException(String.format("Cannot locate classpath manifest '%s' in classpath.", resource));
        }

        List<File> runtimeClasspath = new ArrayList<File>();
        for (String jarName : properties.getProperty("runtime").split(",")) {
            runtimeClasspath.add(findDependencyJar(jarName));
        }

        return new DefaultModule(implementationClasspath, runtimeClasspath);
    }

    private void findImplementationClasspath(String name, List<File> implementationClasspath) {
        List<String> suffixes = new ArrayList<String>();
        Matcher matcher = Pattern.compile("gradle-(.+)").matcher(name);
        matcher.matches();
        String projectDirName = matcher.group(1);
        String projectName = StringUtils.uncapitalize(GUtil.toCamelCase(projectDirName));
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

    private Properties loadModuleProperties(File jarFile, String resource) {
        try {
            ZipFile zipFile = new ZipFile(jarFile);
            try {
                ZipEntry entry = zipFile.getEntry(resource);
                return GUtil.loadProperties(zipFile.getInputStream(entry));
            } finally {
                zipFile.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private File findModuleJar(String name) {
        Pattern pattern = Pattern.compile(Pattern.quote(name) + "-.+\\.jar");
        for (File libDir : libDirs) {
            for (File file : libDir.listFiles()) {
                if (pattern.matcher(file.getName()).matches()) {
                    return file;
                }
            }
        }
        throw new IllegalArgumentException(String.format("Cannot locate JAR for module '%s' in distribution directory '%s'.", name, distDir));
    }

    private File findDependencyJar(String name) {
        File jarFile = classpathJars.get(name);
        if (jarFile != null) {
            return jarFile;
        }
        if (distDir == null) {
            throw new IllegalArgumentException(String.format("Cannot find JAR '%s' using classpath.", name));
        }
        for (File libDir : libDirs) {
            jarFile = new File(libDir, name);
            if (jarFile.isFile()) {
                return jarFile;
            }
        }
        throw new IllegalArgumentException(String.format("Cannot find JAR '%s' using classpath or distribution directory '%s'", name, distDir));
    }

    private static class DefaultModule implements Module {
        private final List<File> implementationClasspath;
        private final List<File> runtimeClasspath;

        public DefaultModule(List<File> implementationClasspath, List<File> runtimeClasspath) {
            this.implementationClasspath = implementationClasspath;
            this.runtimeClasspath = runtimeClasspath;
        }

        public List<File> getImplementationClasspath() {
            return implementationClasspath;
        }

        public List<File> getRuntimeClasspath() {
            return runtimeClasspath;
        }
    }
}
