/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.groovy.scripts.internal;

import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.internal.classloader.ClassLoaderVisitor;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public class GroovyScriptClasspathProvider {
    private final ConcurrentMap<ClassLoaderScope, ClassPath> cachedScopeCompilationClasspath = new ConcurrentHashMap<>();
    private final Map<ClassLoader, Set<File>> cachedClasspaths = new HashMap<>();
    private final Set<File> gradleImplementationClasspath = new LinkedHashSet<>();
    private ClassPath gradleApi;
    private ClassPath groovyJars;
    private final Supplier<Collection<File>> gradleApiJarsProvider;
    private final Supplier<Set<File>> groovyJarsProvider;

    public GroovyScriptClasspathProvider(
        ClassLoaderScope coreAndPluginsScope,
        Supplier<Collection<File>> gradleApiJarsProvider,
        Supplier<Set<File>> groovyJarsProvider
    ) {
        this.gradleApiJarsProvider = gradleApiJarsProvider;
        this.groovyJarsProvider = groovyJarsProvider;
        this.gradleImplementationClasspath.addAll(classpathOf(coreAndPluginsScope.getExportClassLoader()));
        this.gradleImplementationClasspath.removeIf(file -> !file.getName().startsWith("gradle-"));
    }

    public ClassPath compilationClassPathOf(ClassLoaderScope scope) {
        return getGradleApi().plus(exportClassPathFromHierarchyOf(scope));
    }

    private ClassPath getGradleApi() {
        if (gradleApi == null) {
            gradleApi = DefaultClassPath.of(gradleApiJarsProvider.get());
        }
        return gradleApi;
    }

    private ClassPath getGroovy() {
        if (groovyJars == null) {
            groovyJars = DefaultClassPath.of(groovyJarsProvider.get());
        }
        return groovyJars;
    }

    private ClassPath exportClassPathFromHierarchyOf(ClassLoaderScope scope) {
        Set<File> exportedClasspath = new LinkedHashSet<>(classpathOf(scope.getExportClassLoader()));
        exportedClasspath.removeAll(gradleImplementationClasspath);
        return DefaultClassPath.of(exportedClasspath);
    }

    private Set<File> classpathOf(ClassLoader classLoader) {
        Set<File> classpath = cachedClasspaths.get(classLoader);
        if (classpath == null) {
            Set<File> classpathFiles = new LinkedHashSet<>();
            new ClassLoaderVisitor() {
                @Override
                public void visitClassPath(URL[] classPath) {
                    for (URL url : classPath) {
                        if (url.getProtocol().equals("file")) {
                            classpathFiles.add(new File(toUri(url)));
                        }
                    }
                }

                @Override
                public void visitParent(ClassLoader parent) {
                    classpathFiles.addAll(classpathOf(parent));
                }

                private URI toUri(URL url) {
                    try {
                        return url.toURI();
                    } catch (URISyntaxException e) {
                        try {
                            return new URL(
                                url.getProtocol(),
                                url.getHost(),
                                url.getPort(),
                                url.getFile().replace(" ", "%20")
                            ).toURI();
                        } catch (URISyntaxException | MalformedURLException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                }
            }.visit(classLoader);
            cachedClasspaths.put(classLoader, classpathFiles);
            classpath = classpathFiles;
        }
        return classpath;
    }
}
