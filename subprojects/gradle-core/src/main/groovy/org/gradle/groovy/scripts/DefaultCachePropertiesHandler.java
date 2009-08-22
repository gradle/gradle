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
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultCachePropertiesHandler implements CachePropertiesHandler {
    /** {@inheritDoc} */
    public void writeProperties(ScriptSource script, File scriptCacheDir, Map<String, ?> additionalProperties) {
        assert script != null && script.getText() != null;

        Properties properties = new Properties();
        for (Map.Entry<String, ?> entry : additionalProperties.entrySet()) {
            properties.put(entry.getKey(), entry.getValue().toString());
        }
        properties.put(HASH_KEY, HashUtil.createHash(script.getText()));
        properties.put(VERSION_KEY, new GradleVersion().getVersion());

        GUtil.saveProperties(properties, new File(scriptCacheDir, PROPERTY_FILE_NAME));
    }

    /** {@inheritDoc} */
    public CacheState getCacheState(ScriptSource script, File scriptCacheDir, Map<String, ?> additionalProperties) {
        assert script != null && script.getText() != null;
        
        File propertiesFile = new File(scriptCacheDir, PROPERTY_FILE_NAME);
        if (!propertiesFile.isFile()) {
            return CacheState.INVALID;
        }

        Properties properties = GUtil.loadProperties(propertiesFile);

        if (!new GradleVersion().getVersion().equals(properties.get(VERSION_KEY))) {
            return CacheState.INVALID;
        }

        for (Map.Entry<String, ?> entry : additionalProperties.entrySet()) {
            if (!entry.getValue().toString().equals(properties.getProperty(entry.getKey()))) {
                return CacheState.INVALID;
            }
        }

        return HashUtil.createHash(script.getText()).equals(properties.get(HASH_KEY)) ?
                CacheState.VALID : CacheState.INVALID;
    }
}
