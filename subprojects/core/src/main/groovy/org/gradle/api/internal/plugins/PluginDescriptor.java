/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.plugins;

import org.gradle.util.GUtil;

import java.net.URL;
import java.util.Properties;

public class PluginDescriptor {

    private final URL propertiesFileUrl;

    public PluginDescriptor(URL propertiesFileUrl) {
        this.propertiesFileUrl = propertiesFileUrl;
    }

    public String getImplementationClassName() {
        Properties properties = GUtil.loadProperties(propertiesFileUrl);
        return properties.getProperty("implementation-class");
    }

    @Override
    public String toString() {
        return propertiesFileUrl.toString();
    }
}
