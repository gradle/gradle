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
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.protocol.ConnectionFactoryVersion2;
import org.gradle.util.FilteringClassLoader;
import org.gradle.util.GFileUtils;
import org.gradle.util.GradleVersion;
import org.gradle.util.ObservableUrlClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultToolingImplementationLoader implements ToolingImplementationLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultToolingImplementationLoader.class);
    private final ClassLoader classLoader;

    public DefaultToolingImplementationLoader() {
        this(DefaultToolingImplementationLoader.class.getClassLoader());
    }

    DefaultToolingImplementationLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public ConnectionFactoryVersion2 create(Distribution distribution) {
        ClassLoader classLoader = createImplementationClassLoader(distribution);
        String implementationClassName = loadImplementationClassName(classLoader);
        try {
            return (ConnectionFactoryVersion2) classLoader.loadClass(implementationClassName).newInstance();
        } catch (Throwable t) {
            throw new GradleConnectionException(String.format("Could not create an instance of Tooling API implementation class '%s'.", implementationClassName), t);
        }
    }

    private ClassLoader createImplementationClassLoader(Distribution distribution) {
        Set<File> implementationClasspath = distribution.getToolingImplementationClasspath();
        LOGGER.debug("Using tooling provider classpath: {}", implementationClasspath);
        URL[] urls = GFileUtils.toURLArray(implementationClasspath);
        FilteringClassLoader filteringClassLoader = new FilteringClassLoader(classLoader);
        filteringClassLoader.allowPackage("org.gradle.tooling.internal.protocol");
        return new ObservableUrlClassLoader(filteringClassLoader, urls);
    }

    private String loadImplementationClassName(ClassLoader classLoader) {
        try {
            String resourceName = "META-INF/services/" + ConnectionFactoryVersion2.class.getName();
            InputStream inputStream = classLoader.getResourceAsStream(resourceName);
            if (inputStream == null) {
                Matcher m = Pattern.compile("\\w+Version(\\d+)").matcher(ConnectionFactoryVersion2.class.getSimpleName());
                m.matches();
                String protocolVersion = m.group(1);
                throw new UnsupportedVersionException(String.format("The specified Gradle distribution is not supported by this tooling API version (%s, protocol version %s)", GradleVersion.current().getVersion(), protocolVersion));
            }

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
            throw new UnsupportedOperationException(String.format("No implementation class specified in resource '%s'.", resourceName));
        } catch (UnsupportedVersionException e) {
            throw e;
        } catch (Throwable t) {
            throw new GradleConnectionException(String.format("Could not determine class name of Tooling API implementation."), t);
        }
    }
}
