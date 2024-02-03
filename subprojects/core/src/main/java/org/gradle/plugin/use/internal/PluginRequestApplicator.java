/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin.use.internal;

import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.service.scopes.StatefulListener;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;

import javax.annotation.Nullable;

@ServiceScope(Scopes.Build.class)
public interface PluginRequestApplicator {

    /**
     * Resolves the given {@link PluginRequests} into the given {@link ScriptHandlerInternal#getScriptClassPath()},
     * exports the resulting classpath into the given {@link ClassLoaderScope}, closes it and then applies
     * the requested plugins.
     *
     * A null target indicates that no plugin requests should be resolved but only the setup of the given
     * {@link ClassLoaderScope}.
     */
    void applyPlugins(PluginRequests requests, ScriptHandlerInternal scriptHandler, @Nullable PluginManagerInternal target, ClassLoaderScope classLoaderScope);

    @EventScope(Scopes.Build.class)
    @StatefulListener
    interface PluginApplicationListener {

        void pluginApplied(PluginRequestInternal pluginRequest);

    }
}
