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

package org.gradle.plugin.management;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * Allows modification of {@link PluginRequest}s before they are resolved.
 *
 * @since 3.5
 */
@Incubating
@HasInternalProtocol
public interface PluginResolutionStrategy {

    /**
     * Adds an action that is executed for each plugin that is resolved.
     * The {@link PluginResolveDetails} parameter contains information about
     * the plugin that was requested and allows the rule to modify which plugin
     * will actually be resolved.
     */
    void eachPlugin(Action<? super PluginResolveDetails> rule);

}
