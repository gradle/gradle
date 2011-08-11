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
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.util.GUtil;

import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory.ClassPathNotation.GRADLE_API;
import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory.ClassPathNotation.LOCAL_GROOVY;

public class DependencyClassPathProvider extends AbstractClassPathProvider {
    public DependencyClassPathProvider(ClassLoaderRegistry classLoaderRegistry) {
        add(LOCAL_GROOVY.name(), toPatterns("groovy-all"));

        List<Pattern> patterns = new ArrayList<Pattern>();
        ClassLoader classLoader = classLoaderRegistry.getCoreImplClassLoader();
        getRuntimeClasspath("gradle-core", classLoader, patterns);
        getRuntimeClasspath("gradle-core-impl", classLoader, patterns);
        add(GRADLE_API.name(), patterns);
    }

    private void getRuntimeClasspath(String projectName, ClassLoader classLoader, Collection<Pattern> patterns) {
        String resource = String.format("%s-classpath.properties", projectName);
        URL url = classLoader.getResource(resource);
        if (url == null) {
            throw new GradleException(String.format("Cannot find classpath resource '%s'.", resource));
        }
        Properties properties = GUtil.loadProperties(url);
        patterns.addAll(jarNames(Arrays.asList(properties.getProperty("runtime").split(","))));
        patterns.addAll(toPatterns(projectName));
    }
}
