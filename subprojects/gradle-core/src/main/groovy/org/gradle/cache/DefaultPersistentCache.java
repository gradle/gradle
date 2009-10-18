/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.cache;

import org.gradle.CacheUsage;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Map;
import java.util.Properties;

public class DefaultPersistentCache implements PersistentCache {
    private final File dir;
    private final File propertiesFile;
    private final Properties properties = new Properties();
    private boolean valid;

    public DefaultPersistentCache(File dir, CacheUsage cacheUsage, Map<String, ?> properties) {
        this.dir = dir;
        propertiesFile = new File(dir, "cache.properties");
        this.properties.putAll(properties);
        determineIfCacheIsValid(cacheUsage, properties);
        buildCacheDir();
    }

    private void buildCacheDir() {
        if (!valid) {
            GFileUtils.deleteDirectory(dir);
        }
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
    }

    private void determineIfCacheIsValid(CacheUsage cacheUsage, Map<String, ?> properties) {
        valid = false;

        if (cacheUsage != CacheUsage.ON) {
            return;
        }

        if (!propertiesFile.isFile()) {
            return;
        }

        Properties currentProperties = GUtil.loadProperties(propertiesFile);
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            if (!entry.getValue().toString().equals(currentProperties.getProperty(entry.getKey()))) {
                return;
            }
        }
        valid = true;
    }

    public Properties getProperties() {
        return properties;
    }

    public File getBaseDir() {
        return dir;
    }

    public boolean isValid() {
        return valid;
    }

    public void update() {
        GUtil.saveProperties(properties, propertiesFile);
        valid = true;
    }
}
