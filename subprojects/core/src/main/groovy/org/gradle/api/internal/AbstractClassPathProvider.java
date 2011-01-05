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

import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.util.ClasspathUtil;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

public abstract class AbstractClassPathProvider implements ClassPathProvider, GradleDistributionLocator {
    private final List<Pattern> all = Arrays.asList(Pattern.compile(".+"));
    private final Map<String, List<Pattern>> classPaths = new HashMap<String, List<Pattern>>();
    private final Scanner pluginLibs;
    private final Scanner runtimeLibs;
    private final File gradleHome;

    protected AbstractClassPathProvider() {
        File codeSource = getClasspathForClass(DefaultClassPathProvider.class);
        if (codeSource.isFile()) {
            // Loaded from a JAR - assume we're running from the distribution
            gradleHome = codeSource.getParentFile().getParentFile();
            runtimeLibs = new DirScanner(new File(gradleHome + "/lib"));
            pluginLibs = new DirScanner(new File(gradleHome + "/lib/plugins"));
        } else {
            // Loaded from a classes dir - assume we're running from the ide or tests
            gradleHome = null;
            runtimeLibs = new ClassPathScanner(codeSource);
            pluginLibs = runtimeLibs;
        }
    }

    public File getGradleHome() {
        return gradleHome;
    }

    protected void add(String name, List<Pattern> patterns) {
        classPaths.put(name, patterns);
    }

    protected static List<Pattern> toPatterns(String... patternStrings) {
        List<Pattern> patterns = new ArrayList<Pattern>();
        for (String patternString : patternStrings) {
            patterns.add(Pattern.compile(patternString + "-.+"));
        }
        return patterns;
    }

    public Set<File> findClassPath(String name) {
        Set<File> matches = new LinkedHashSet<File>();
        if (name.equals("GRADLE_PLUGINS")) {
            pluginLibs.find(all, matches);
            return matches;
        }
        if (name.equals("GRADLE_RUNTIME")) {
            runtimeLibs.find(all, matches);
            return matches;
        }
        List<Pattern> classPathPatterns = classPaths.get(name);
        if (classPathPatterns != null) {
            runtimeLibs.find(classPathPatterns, matches);
            pluginLibs.find(classPathPatterns, matches);
            return matches;
        }
        return null;
    }

    public static File getClasspathForClass(Class<?> targetClass) {
        URI location;
        try {
            location = targetClass.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (URISyntaxException e) {
            throw new UncheckedIOException(e);
        }
        if (!location.getScheme().equals("file")) {
            throw new GradleException(String.format("Cannot determine Gradle home using codebase '%s'.", location));
        }
        return new File(location.getPath());
    }

    private static boolean matches(Iterable<Pattern> patterns, String name) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    private interface Scanner {
        void find(Iterable<Pattern> patterns, Collection<File> into);
    }

    private static class DirScanner implements Scanner {
        private final File dir;

        private DirScanner(File dir) {
            this.dir = dir;
        }

        public void find(Iterable<Pattern> patterns, Collection<File> into) {
            for (File file : dir.listFiles()) {
                if (matches(patterns, file.getName())) {
                    into.add(file);
                }
            }
        }
    }

    // This is used when running from the IDE or junit tests
    private static class ClassPathScanner implements Scanner {
        private final File classesDir;
        private final Collection<URL> classpath;

        private ClassPathScanner(File classesDir) {
            this.classesDir = classesDir;
            this.classpath = ClasspathUtil.getClasspath(getClass().getClassLoader());
        }

        public void find(Iterable<Pattern> patterns, Collection<File> into) {
            if (matches(patterns, "gradle-core-version.jar")) {
                into.add(classesDir);
            }
            for (URL url : classpath) {
                if (url.getProtocol().equals("file")) {
                    try {
                        File file = new File(url.toURI());
                        if (matches(patterns, file.getName())) {
                            into.add(file);
                        }
                    } catch (URISyntaxException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
        }
    }
}
