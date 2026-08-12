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

import com.google.common.collect.ImmutableList;
import org.gradle.util.internal.GUtil;

import java.net.URL;
import java.util.List;
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

    /**
     * Module coordinates ({@code group:name}, comma-separated) of distribution components that must
     * join the script classpath when this plugin is applied, resolved at the running distribution's
     * version. Only meaningful for plugins shipped in the Gradle distribution: a distribution
     * plugin's own classes bypass artifact resolution, so a companion library it needs on the
     * consuming build's classpath (e.g. a built-in XDCL ecosystem's published schema library, served
     * by the distribution-embedded Maven repository) is declared here and injected by core plugin
     * resolution. Empty when the descriptor declares none.
     */
    public List<String> getDistributionCompanionModules() {
        Properties properties = GUtil.loadProperties(propertiesFileUrl);
        String declared = properties.getProperty("distribution-companion-modules");
        if (declared == null || declared.trim().isEmpty()) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<String> modules = ImmutableList.builder();
        for (String module : declared.split(",")) {
            String trimmed = module.trim();
            if (!trimmed.isEmpty()) {
                modules.add(trimmed);
            }
        }
        return modules.build();
    }

    public URL getPropertiesFileUrl() {
        return propertiesFileUrl;
    }

    @Override
    public String toString() {
        return propertiesFileUrl.toString();
    }
}
