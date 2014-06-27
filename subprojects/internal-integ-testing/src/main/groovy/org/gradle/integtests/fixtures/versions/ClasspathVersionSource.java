/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.fixtures.versions;

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.Factory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

public class ClasspathVersionSource implements Factory<Properties> {

    private final String resourceName;
    private final ClassLoader classLoader;

    public ClasspathVersionSource() {
        this("all-released-versions.properties", ClasspathVersionSource.class.getClassLoader());
    }

    ClasspathVersionSource(String resourceName, ClassLoader classLoader) {
        this.resourceName = resourceName;
        this.classLoader = classLoader;
    }

    public Properties create() {
        URL resource = classLoader.getResource(resourceName);
        if (resource == null) {
            throw new RuntimeException(
                    "Unable to find the released versions information.\n"
                            + "The resource '" + resourceName + "' was not found.\n"
                            + "Most likely, you haven't ran the 'prepareVersionsInfo' task.\n"
                            + "If you have trouble running tests from your IDE, please run gradlew idea|eclipse first."
            );
        }

        try {
            Properties properties = new Properties();
            InputStream stream = resource.openStream();
            try {
                properties.load(stream);
            } finally {
                stream.close();
            }
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
