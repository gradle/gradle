/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Rule;

import java.util.List;

/**
 * @author Hans Dockter
 */
public interface PluginContainer extends PluginCollection<Plugin> {
    boolean hasPlugin(String id);

    boolean hasPlugin(Class<? extends Plugin> type);

    /**
     * Adds a rule to this container. The given rule is invoked when an unknown plugin is requested.
     *
     * @param rule The rule to add.
     * @return The added rule.
     */
    Rule addRule(Rule rule);

    /**
     * Returns the rules used by this container.
     *
     * @return The rules, in the order they will be applied.
     */
    List<Rule> getRules();

    Plugin findPlugin(String id);

    Plugin findPlugin(Class<? extends Plugin> type);
}
