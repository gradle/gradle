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
import org.gradle.api.Action;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Map;
import java.util.Properties;

public class DefaultPersistentDirectoryCache implements PersistentCache {
    private final File dir;
    private final File propertiesFile;
    private final Properties properties = new Properties();

    public DefaultPersistentDirectoryCache(File dir, CacheUsage cacheUsage, Map<String, ?> properties, Action<? super PersistentCache> initAction) {
        this.dir = dir;
        propertiesFile = new File(dir, "cache.properties");
        this.properties.putAll(properties);
        boolean valid = determineIfCacheIsValid(cacheUsage, properties);
        buildCacheDir(initAction, valid);
    }

    @Override
    public String toString() {
        return String.format("Cache %s", dir);
    }

    private void buildCacheDir(Action<? super PersistentCache> initAction, boolean valid) {
        if (!valid) {
            GFileUtils.deleteDirectory(dir);
        }
        if (!valid) {
            dir.mkdirs();
            if (initAction != null) {
                initAction.execute(this);
            }
            GUtil.saveProperties(properties, propertiesFile);
            valid = true;
        }
    }

    private boolean determineIfCacheIsValid(CacheUsage cacheUsage, Map<String, ?> properties) {
        if (cacheUsage != CacheUsage.ON) {
            return false;
        }

        if (!propertiesFile.isFile()) {
            return false;
        }

        Properties currentProperties = GUtil.loadProperties(propertiesFile);
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            if (!entry.getValue().toString().equals(currentProperties.getProperty(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    public Properties getProperties() {
        return properties;
    }

    public File getBaseDir() {
        return dir;
    }
}
