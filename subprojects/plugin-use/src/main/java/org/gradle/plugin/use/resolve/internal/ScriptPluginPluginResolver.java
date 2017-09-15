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
package org.gradle.plugin.use.resolve.internal;

import org.gradle.api.internal.plugins.PluginImplementation;
import org.gradle.plugin.use.PluginId;

import javax.annotation.Nullable;

/**
 * Plugin resolver for script plugins.
 */
public class ScriptPluginPluginResolver implements PluginResolver {

    private final ScriptPluginLoaderCache scriptPluginLoaderCache;

    public ScriptPluginPluginResolver(ScriptPluginLoaderCache scriptPluginLoaderCache) {
        this.scriptPluginLoaderCache = scriptPluginLoaderCache;
    }

    public static String getDescription() {
        return "Script Plugins";
    }

    @Override
    public void resolve(ContextAwarePluginRequest pluginRequest, PluginResolutionResult result) {
        if (pluginRequest.getScript() == null) {
            return;
        }

        ScriptPluginImplementation scriptPluginImplementation = new ScriptPluginImplementation(pluginRequest, scriptPluginLoaderCache);
        result.found(getDescription(), new SimplePluginResolution(scriptPluginImplementation));
    }

    private static class ScriptPluginImplementation implements PluginImplementation<Object> {

        private final ContextAwarePluginRequest pluginRequest;
        private final ScriptPluginLoaderCache scriptPluginLoaderCache;

        private ScriptPluginLoader scriptPluginLoader;

        private ScriptPluginImplementation(ContextAwarePluginRequest pluginRequest, ScriptPluginLoaderCache scriptPluginLoaderCache) {
            this.pluginRequest = pluginRequest;
            this.scriptPluginLoaderCache = scriptPluginLoaderCache;
        }

        private ScriptPluginLoader scriptPluginLoader() {
            if (scriptPluginLoader == null) {
                scriptPluginLoader = scriptPluginLoaderCache.scriptPluginLoaderFor(pluginRequest, getDisplayName());
            }
            return scriptPluginLoader;
        }

        @Override
        public Class<?> asClass() {
            return scriptPluginLoader().getImplementationClass();
        }

        @Override
        public boolean isImperative() {
            return true;
        }

        @Override
        public boolean isHasRules() {
            return false;
        }

        @Override
        public Type getType() {
            return Type.IMPERATIVE_CLASS;
        }

        @Override
        public String getDisplayName() {
            return "script plugin '" + pluginRequest.getRelativeScriptUri() + "'";
        }

        @Nullable
        @Override
        public PluginId getPluginId() {
            return pluginRequest.getId();
        }

        @Override
        public boolean isAlsoKnownAs(PluginId id) {
            return id.equals(getPluginId());
        }
    }
}
