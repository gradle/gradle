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

import org.gradle.internal.event.ListenerManager;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class DefaultFeatureFlags implements FeatureFlags {
    private final Set<FeatureFlag> enabled = new CopyOnWriteArraySet<FeatureFlag>();
    private final InternalOptions options;
    private final FeatureFlagListener broadcaster;

    public DefaultFeatureFlags(InternalOptions options, ListenerManager listenerManager) {
        this.options = options;
        this.broadcaster = listenerManager.getBroadcaster(FeatureFlagListener.class);
    }

    @Override
    public boolean isEnabled(FeatureFlag flag) {
        broadcaster.flagRead(flag);
        if (flag.getSystemPropertyName() != null) {
            // Can explicitly disable property using system property
            Option.Value<Boolean> option = options.getOption(new InternalFlag(flag.getSystemPropertyName()));
            if (option.isExplicit() || option.get()) {
                return option.get();
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
