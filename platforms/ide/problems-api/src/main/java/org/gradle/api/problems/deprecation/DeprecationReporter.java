/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.problems.deprecation;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.problems.internal.Problem;

/**
 * Specialized reporter for deprecation problems.
 *
 * @since 8.12
 */
@Incubating
public interface DeprecationReporter {

    /**
     * Generic deprecation
     *
     * @param message a message for the deprecation; it should state the deprecation, but not the reason (e.g. "Plugin 'plugin' is deprecated")
     * @param feature a spec to configure the deprecation
     * @return a problem representing the deprecation
     */
    Problem deprecate(String message, Action<DeprecateGenericSpec> feature);

    /**
     * Deprecates the <i>current</i> method.
     *
     * @param spec a spec to configure the deprecation
     * @return a problem representing the deprecation
     */
    Problem deprecateMethod(Action<DeprecateMethodSpec> spec);

    /**
     * Deprecates a given method.
     * <p>
     * Representation of the signature is created by the called.
     *
     * @param spec a spec to configure the deprecation
     * @return a problem representing the deprecation
     */
    Problem deprecateMethod(String method, Action<DeprecateMethodSpec> spec);

    /**
     * Deprecates the <i>current</i> plugin.
     *
     * @param spec a spec to configure the deprecation
     * @return a problem representing the deprecation
     */
    Problem deprecatePlugin(Action<DeprecatePluginSpec> spec);

    /**
     * Deprecates a given plugin.
     *
     * @param pluginId the id of the plugin to deprecate
     * @param spec a spec to configure the deprecation
     */
    Problem deprecatePlugin(String pluginId, Action<DeprecatePluginSpec> spec);


}
