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
package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.internal.protocol.ConnectionFactoryVersion1;
import org.gradle.util.FilteringClassLoader;
import org.gradle.util.GFileUtils;
import org.gradle.util.ObservableUrlClassLoader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

public class DefaultToolingImplementationLoader implements ToolingImplementationLoader {
    private final ClassLoader classLoader;

    public DefaultToolingImplementationLoader() {
        classLoader = getClass().getClassLoader();
    }

    public ConnectionFactoryVersion1 create(Distribution distribution) {
        ClassLoader classLoader = createImplementationClassLoader(distribution);
        String implementationClassName = loadImplementationClassName(classLoader);
        try {
            return (ConnectionFactoryVersion1) classLoader.loadClass(implementationClassName).newInstance();
        } catch (Throwable t) {
            throw new GradleConnectionException(String.format("Could not create an instance of Tooling API implementation class '%s'.", implementationClassName), t);
        }
    }

    private ClassLoader createImplementationClassLoader(Distribution distribution) {
        URL[] urls = GFileUtils.toURLArray(distribution.getToolingImplementationClasspath());
        FilteringClassLoader filteringClassLoader = new FilteringClassLoader(classLoader);
        filteringClassLoader.allowPackage("org.gradle.tooling.internal.protocol");
        return new ObservableUrlClassLoader(filteringClassLoader, urls);
    }

    private String loadImplementationClassName(ClassLoader classLoader) {
        try {
            InputStream inputStream = classLoader.getResourceAsStream("META-INF/services/org.gradle.tooling.internal.protocol.GradleConnectionFactoryVersion1");
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.replaceAll("#.*", "").trim();
                    if (line.length() > 0) {
                        return line;
                    }
                }
            } finally {
                inputStream.close();
            }
            throw new UnsupportedOperationException();
        } catch (Throwable t) {
            throw new GradleConnectionException(String.format("Could not determine class name of Tooling API implementation."), t);
        }
    }
}
