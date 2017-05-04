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

package org.gradle.caching.internal;

import java.util.Map;

public final class BuildCacheWrapper {

    private final String className;
    private final boolean enabled;
    private final boolean push;
    private final String displayName;
    private final Map<String, String> config;

    public BuildCacheWrapper(String className, boolean enabled, boolean push, String displayName, Map<String, String> config) {
        this.className = className;
        this.enabled = enabled;
        this.push = push;
        this.displayName = displayName;
        this.config = config;
    }

    public String getClassName() {
        return className;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPush() {
        return push;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Map<String, String> getConfig() {
        return config;
    }
}
