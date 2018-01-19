/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugin.use.resolve.internal;

import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.use.PluginId;

import javax.annotation.Nullable;

// TODO: Add context information
public class SelfResolvingPluginRequest implements PluginRequestInternal {
    @Override
    public boolean isApply() {
        return false;
    }

    @Override
    public Integer getLineNumber() {
        return null;
    }

    @Override
    public String getScriptDisplayName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public PluginRequestInternal getOriginalRequest() {
        return null;
    }

    @Override
    public PluginId getId() {
        return null;
    }

    @Nullable
    @Override
    public String getVersion() {
        return null;
    }

    @Nullable
    @Override
    public ModuleVersionSelector getModule() {
        return null;
    }
}
