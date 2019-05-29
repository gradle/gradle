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

package org.gradle.buildinit.plugins.internal;

import org.gradle.api.Action;

import javax.annotation.Nullable;

public interface CrossConfigurationScriptBlockBuilder extends ScriptBlockBuilder {
    /**
     * Allows repository definitions to be added to the target of this block.
     */
    RepositoriesBuilder repositories();

    /**
     * Allows dependencies to be added to the target of this block.
     */
    DependenciesBuilder dependencies();

    /**
     * Adds a plugin to be applied to the target of this block.
     *
     * @param comment A description of why the plugin is required
     */
    void plugin(@Nullable String comment, String pluginId);

    /**
     * Adds a property assignment statement to the configuration of all tasks with the given type of the target of this block.
     */
    void taskPropertyAssignment(@Nullable String comment, String taskType, String propertyName, Object propertyValue);

    /**
     * Adds a property assignment statement to the configuration of all tasks with the given type of the target of this block.
     */
    void taskMethodInvocation(@Nullable String comment, String taskName, String taskType, String methodName, Object... methodArgs);

    /**
     * Creates a task in the target of this block.
     *
     * @return an expression that can be used to refer to the task
     */
    BuildScriptBuilder.Expression taskRegistration(@Nullable String comment, String taskName, String taskType, Action<? super ScriptBlockBuilder> blockContentBuilder);
}
