/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testkit.runner.internal;

import org.gradle.testkit.runner.InvalidPluginMetadataException;
import org.gradle.util.GUtil;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public abstract class PluginUnderTestMetadataReading {

    public static final String IMPLEMENTATION_CLASSPATH_PROP_KEY = "implementation-classpath";
    public static final String PLUGIN_METADATA_FILE_NAME = "plugin-under-test-metadata.properties";

    private PluginUnderTestMetadataReading() {
    }

    public static List<File> readImplementationClasspath() {
        return readImplementationClasspath(Thread.currentThread().getContextClassLoader());
    }

    public static List<File> readImplementationClasspath(ClassLoader classLoader) {
        URL pluginClasspathUrl = classLoader.getResource(PLUGIN_METADATA_FILE_NAME);

        if (pluginClasspathUrl == null) {
            throw new InvalidPluginMetadataException(String.format("Test runtime classpath does not contain plugin metadata file '%s'", PLUGIN_METADATA_FILE_NAME));
        }

        return readImplementationClasspath(pluginClasspathUrl);
    }

    public static List<File> readImplementationClasspath(URL pluginClasspathUrl) {
        return readImplementationClasspath(pluginClasspathUrl.toString(), GUtil.loadProperties(pluginClasspathUrl));
    }

    public static List<File> readImplementationClasspath(String description, Properties properties) {
        if (!properties.containsKey(IMPLEMENTATION_CLASSPATH_PROP_KEY)) {
            throw new InvalidPluginMetadataException(String.format("Plugin metadata file '%s' does not contain expected property named '%s'", description, IMPLEMENTATION_CLASSPATH_PROP_KEY));
        }

        String value = properties.getProperty(IMPLEMENTATION_CLASSPATH_PROP_KEY);
        if (value != null) {
            value = value.trim();
        }

        if (value == null || value.isEmpty()) {
            throw new InvalidPluginMetadataException(String.format("Plugin metadata file '%s' has empty value for property named '%s'", description, IMPLEMENTATION_CLASSPATH_PROP_KEY));
        }

        String[] parsedImplementationClasspath = value.trim().split(File.pathSeparator);
        List<File> files = new ArrayList<File>(parsedImplementationClasspath.length);
        for (String path : parsedImplementationClasspath) {
            files.add(new File(path));
        }
        return files;
    }

}
