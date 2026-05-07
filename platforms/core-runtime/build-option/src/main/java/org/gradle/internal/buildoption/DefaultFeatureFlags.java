/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.buildoption;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class DefaultFeatureFlags implements FeatureFlags {
    private final Set<FeatureFlag> enabled = new CopyOnWriteArraySet<FeatureFlag>();
    private final FeatureFlagListener broadcaster;
    private final Map<String, String> startParameterSystemProperties;

    public DefaultFeatureFlags(FeatureFlagListener listener, Map<String, String> startParameterSystemProperties) {
        this.startParameterSystemProperties = startParameterSystemProperties;
        this.broadcaster = listener;
    }

    @Override
    public boolean isEnabled(FeatureFlag flag) {
        broadcaster.flagRead(flag);
        if (flag.getSystemPropertyName() != null) {
            String systemPropertyValue = startParameterSystemProperties.get(flag.getSystemPropertyName());
            if (systemPropertyValue != null) {
                return BooleanOptionUtil.isTrue(systemPropertyValue);
            }
        }
        return enabled.contains(flag);
    }

    @Override
    public void enable(FeatureFlag flag) {
        enabled.add(flag);
    }

    @Override
    public boolean isEnabledWithApi(FeatureFlag flag) {
        return enabled.contains(flag);
    }
}
