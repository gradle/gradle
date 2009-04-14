/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.groovy.scripts;

import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.gradle.util.HashUtil;

import java.io.File;
import java.util.Properties;

/**
 * @author Hans Dockter
 */
public class DefaultCachePropertiesHandler implements CachePropertiesHandler {
    public void writeProperties(String scriptText, File scriptCacheDir) {
        assert scriptText != null;

        Properties properties = new Properties();
        properties.put(CachePropertiesHandler.HASH_KEY, HashUtil.createHash(scriptText));
        properties.put(CachePropertiesHandler.VERSION_KEY, new GradleVersion().getVersion());
        
        GUtil.saveProperties(properties, new File(scriptCacheDir, CachePropertiesHandler.PROPERTY_FILE_NAME));
    }

    public CacheState getCacheState(String scriptText, File scriptCacheDir) {
        assert scriptText != null;
        
        File propertiesFile = new File(scriptCacheDir, CachePropertiesHandler.PROPERTY_FILE_NAME);
        if (!propertiesFile.isFile()) {
            return CacheState.INVALID;
        }
        Properties properties = GUtil.loadProperties(new File(scriptCacheDir, CachePropertiesHandler.PROPERTY_FILE_NAME));

        if (!properties.get(CachePropertiesHandler.VERSION_KEY).equals(new GradleVersion().getVersion())) {
            return CacheState.INVALID;
        }
        return HashUtil.createHash(scriptText).equals(properties.get(CachePropertiesHandler.HASH_KEY)) ?
                CacheState.VALID : CacheState.INVALID;
    }
}
