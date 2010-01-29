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

import java.io.File;
import java.net.*;
import java.util.*;
import java.util.regex.Pattern;

public class DefaultClassPathRegistry implements ClassPathRegistry {
    private final Scanner pluginLibDir;
    private final Scanner runtimeLibDir;
    private final List<Pattern> all = Arrays.asList(Pattern.compile(".+"));
    private final Map<String, List<Pattern>> classPaths = new HashMap<String, List<Pattern>>();

    public DefaultClassPathRegistry() {
        URI location;
        try {
            location = DefaultClassPathRegistry.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (URISyntaxException e) {
            throw new UncheckedIOException(e);
        }
        if (!location.getScheme().equals("file")) {
            throw new GradleException(String.format("Cannot determine Gradle home using codebase '%s'.", location));
        }
        File codeSource = new File(location.getPath());
        if (codeSource.isFile()) {
            File gradleHome = codeSource.getParentFile().getParentFile();
            runtimeLibDir = new DirScanner(new File(gradleHome + "/lib"));
            pluginLibDir = new DirScanner(new File(gradleHome + "/lib/plugins"));
        } else {
            runtimeLibDir = new ClassPathScanner(codeSource);
            pluginLibDir = new ClassPathScanner(codeSource);
        }
        
        List<Pattern> groovyPatterns = toPatterns("groovy-all");
        classPaths.put("LOCAL_GROOVY", groovyPatterns);
        List<Pattern> gradleApiPatterns = toPatterns("gradle-\\w+", "ivy", "slf4j");
        gradleApiPatterns.addAll(groovyPatterns);
        classPaths.put("GRADLE_API", gradleApiPatterns);
        classPaths.put("GRADLE_CORE", toPatterns("gradle-core"));
        classPaths.put("ANT", toPatterns("ant", "ant-launcher"));
        classPaths.put("ANT_JUNIT", toPatterns("ant", "ant-launcher", "ant-junit"));
        List<Pattern> loggingPatterns = toPatterns("slf4j-api", "logback-classic", "logback-core");
        List<Pattern> testListenerPatterns = toPatterns("gradle-core", "gradle-plugins", "commons-lang");
        testListenerPatterns.addAll(loggingPatterns);
        classPaths.put("TEST_LISTENER", testListenerPatterns);
        classPaths.put("COMMONS_CLI", toPatterns("commons-cli"));
        List<Pattern> workerProcessPatterns = toPatterns("gradle-core");
        workerProcessPatterns.addAll(loggingPatterns);
        classPaths.put("WORKER_PROCESS", workerProcessPatterns);
    }

    private static List<Pattern> toPatterns(String... patternStrings) {
        List<Pattern> patterns = new ArrayList<Pattern>();
        for (String patternString : patternStrings) {
            patterns.add(Pattern.compile(patternString + "-.+"));
        }
        return patterns;
    }

    public URL[] getClassPathUrls(String name) {
        return toURIs(getClassPathFiles(name));
    }

    public Set<File> getClassPathFiles(String name) {
        Set<File> matches = new LinkedHashSet<File>();
        if (name.equals("GRADLE_PLUGINS")) {
            pluginLibDir.find(all, matches);
            return matches;
        }
        if (name.equals("GRADLE_RUNTIME")) {
            runtimeLibDir.find(all, matches);
            return matches;
        }
        List<Pattern> classPathPatterns = classPaths.get(name);
        if (classPathPatterns != null) {
            runtimeLibDir.find(classPathPatterns, matches);
            pluginLibDir.find(classPathPatterns, matches);
            return matches;
        }
        throw new IllegalArgumentException(String.format("unknown classpath '%s' requested.", name));
    }

    private URL[] toURIs(Collection<File> files) {
        List<URL> urls = new ArrayList<URL>(files.size());
        for (File file : files) {
            try {
                urls.add(file.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new UncheckedIOException(e);
            }
        }
        return urls.toArray(new URL[urls.size()]);
    }

    private static boolean matches(List<Pattern> patterns, String name) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    private interface Scanner {
        void find(List<Pattern> patterns, Collection<File> into);
    }

    private static class DirScanner implements Scanner {
        private final File dir;

        private DirScanner(File dir) {
            this.dir = dir;
        }

        public void find(List<Pattern> patterns, Collection<File> into) {
            for (File file : dir.listFiles()) {
                if (matches(patterns, file.getName())) {
                    into.add(file);
                }
            }
        }
    }

    private static class ClassPathScanner implements Scanner {
        private final File classesDir;

        private ClassPathScanner(File classesDir) {
            this.classesDir = classesDir;
        }

        public void find(List<Pattern> patterns, Collection<File> into) {
            if (matches(patterns, "gradle-core-version.jar")) {
                into.add(classesDir);
            }

            ClassLoader classLoader = getClass().getClassLoader();
            if (classLoader instanceof URLClassLoader) {
                URLClassLoader urlClassLoader = (URLClassLoader) classLoader;
                URL[] urls = urlClassLoader.getURLs();
                for (URL url : urls) {
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
}
