/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.jvm;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

import static java.lang.String.format;

public class GradleVersionNumberLoader {

    private static final String RESOURCE_NAME = "org/gradle/build-receipt.properties";
    private static final String VERSION_NUMBER_PROPERTY = "versionNumber";

    public static String loadGradleVersionNumber() {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(RESOURCE_NAME);
        if (resource == null) {
            throw new RuntimeException(format("Resource '%s' not found.", RESOURCE_NAME));
        }
        try {
            InputStream inputStream = null;
            try {
                URLConnection connection = resource.openConnection();
                connection.setUseCaches(false);
                inputStream = connection.getInputStream();
                Properties properties = new Properties();
                properties.load(inputStream);

                return properties.get(VERSION_NUMBER_PROPERTY).toString();
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(format("Could not load version details from resource '%s'.", resource), e);
        }
    }
}
