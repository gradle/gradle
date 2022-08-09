/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.internal.artifacts.dsl.ModuleVersionSelectorParsers;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.PluginResolveDetails;

public class DefaultPluginResolveDetails implements PluginResolveDetails {
    private static final NotationParser<Object, ModuleVersionSelector> USE_MODULE_NOTATION_PARSER = ModuleVersionSelectorParsers.parser("useModule()");

    private final PluginRequestInternal pluginRequest;
    private PluginRequestInternal targetPluginRequest;

    public DefaultPluginResolveDetails(PluginRequestInternal pluginRequest) {
        this.pluginRequest = pluginRequest;
        this.targetPluginRequest = pluginRequest;
    }

    @Override
    public PluginRequest getRequested() {
        return pluginRequest;
    }

    @Override
    public void useModule(Object notation) {
        targetPluginRequest = new DefaultPluginRequest(
            targetPluginRequest.getId(),
            targetPluginRequest.getVersion(),
            targetPluginRequest.isApply(),
            targetPluginRequest.getLineNumber(),
            targetPluginRequest.getScriptDisplayName(),
            USE_MODULE_NOTATION_PARSER.parseNotation(notation),
            targetPluginRequest.getOriginalRequest()
        );
    }

    @Override
    public void useVersion(String version) {
        targetPluginRequest = new DefaultPluginRequest(
            targetPluginRequest.getId(),
            version,
            targetPluginRequest.isApply(),
            targetPluginRequest.getLineNumber(),
            targetPluginRequest.getScriptDisplayName(),
            targetPluginRequest.getModule(),
            targetPluginRequest.getOriginalRequest()
        );
    }

    @Override
    public PluginRequestInternal getTarget() {
        return targetPluginRequest;
    }
}
