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

package org.gradle.plugin.use;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * The DSL for declaring plugins to use for a project.
 */
@Incubating
public interface ProjectPluginDependenciesSpec extends PluginDependenciesSpec{

    /**
     * Configures the plugins on this project and all its sub-projects.
     *
     * @param configuration the configuration to apply
     */
    void allprojects(Action<? super ProjectPluginDependenciesSpec> configuration);

    /**
     * Configures the plugins on all of this project's sub-projects
     * @param configuration
     */
    void subprojects(Action<? super ProjectPluginDependenciesSpec> configuration);

}
