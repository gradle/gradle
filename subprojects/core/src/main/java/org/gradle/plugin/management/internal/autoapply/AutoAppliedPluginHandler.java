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

import org.gradle.plugin.management.internal.PluginRequests;

/**
 * <p>
 * Certain plugins are important enough that Gradle should auto-apply them when it is clear
 * that the user is trying to use it. For instance, when the user uses the <code>--scan</code> option, it
 * is clear they expect the build scan plugin to be applied.
 * </p>
 * Auto-application of a plugin is skipped in the following situations, so the user can adjust the version they want:
 * <ul>
 * <li> The plugin was already applied (e.g. through an init script)
 * <li> The plugin was already requested in the <code>plugins {}</code> block </li>
 * <li> The plugin was already requested in the <code>buildscript {}</code> block </li>
 * </ul>
 * <p>
 * Gradle also allows for automatically loading plugins via a system property, this class
 * understands how to discover these plugin requests as well.
 */
public interface AutoAppliedPluginHandler {
    /**
     * Returns plugin requests that should be auto-applied
     * based on user requests, the current build invocation and the given target.
     */
    PluginRequests getAutoAppliedPlugins(PluginRequests initialRequests, Object pluginTarget);
}
