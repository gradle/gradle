/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.configuration;

import groovy.lang.Script;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.groovy.scripts.BasicScript;

/**
 * A view over the target of a script. Represents the DSL that will be applied to the target.
 */
public interface ScriptTarget {
    /**
     * Returns a unique id for the DSL, used in paths and such.
     */
    String getId();

    /**
     * Attaches the target's main script to the target, if it needs it
     */
    void attachScript(Script script);

    String getClasspathBlockName();

    Class<? extends BasicScript> getScriptClass();

    boolean getSupportsPluginsBlock();

    boolean getSupportsPluginRepositoriesBlock();

    boolean getSupportsMethodInheritance();

    PluginManagerInternal getPluginManager();

    /**
     * Add a configuration action to be applied to the target.
     *
     * @param runnable The action. Should be run in the order provided.
     * @param deferrable true when the action can be deferred
     */
    void addConfiguration(Runnable runnable, boolean deferrable);
}
