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
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.plugin.management.internal.PluginRequests;

// Implementation is provided by 'plugin-use' module
public interface PluginRequestApplicator {
    void applyPlugins(ScriptSource requestingScriptSource, PluginRequests requests, ScriptHandlerInternal scriptHandler, PluginManagerInternal target, ClassLoaderScope classLoaderScope);
}
