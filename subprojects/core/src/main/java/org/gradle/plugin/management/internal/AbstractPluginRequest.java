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
package org.gradle.plugin.management.internal;

import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.plugin.use.PluginId;

import javax.annotation.Nullable;
import java.net.URI;

abstract class AbstractPluginRequest implements PluginRequestInternal {

    private final String requestingScriptDisplayName;
    private final int requestingScriptLineNumber;

    AbstractPluginRequest(String requestingScriptDisplayName, int requestingScriptLineNumber) {
        this.requestingScriptDisplayName = requestingScriptDisplayName;
        this.requestingScriptLineNumber = requestingScriptLineNumber;
    }

    @Nullable
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
    public URI getScript() {
        return null;
    }

    @Nullable
    @Override
    public ModuleVersionSelector getModule() {
        return null;
    }

    @Override
    public boolean isApply() {
        return true;
    }

    @Override
    public int getRequestingScriptLineNumber() {
        return requestingScriptLineNumber;
    }

    @Override
    public String getRequestingScriptDisplayName() {
        return requestingScriptDisplayName;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
