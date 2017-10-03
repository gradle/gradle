/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.plugin.management.internal.autoapply;

import com.google.common.collect.Maps;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;

import java.util.Map;

/**
 * A registry of auto-applied plugins and their corresponding version.
 *
 * @since 4.3
 */
public final class AutoAppliedPluginRegistry {

    private static Map<PluginId, String> autoAppliedPlugins = Maps.newHashMap();

    static {
        autoAppliedPlugins.put(new DefaultPluginId("com.gradle.build-scan"), "1.10");
    }

    private AutoAppliedPluginRegistry() {
    }

    public static String lookupVersion(PluginId pluginId) {
        if (!autoAppliedPlugins.containsKey(pluginId)) {
            throw new UnregisteredAutoAppliedPluginException(pluginId);
        }

        return autoAppliedPlugins.get(pluginId);
    }
}
