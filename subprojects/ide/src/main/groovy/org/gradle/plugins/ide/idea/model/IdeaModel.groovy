/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.ide.idea.model

import org.gradle.util.ConfigureUtil

/**
 * DSL-friendly model of the IDEA project information.
 * First point of entry when it comes to customizing the IDEA generation.
 * <p>
 * See the examples in docs for {@link IdeaModule} or {@link IdeaProject}.
 * <p>
 */
class IdeaModel {

    /**
     * Configures IDEA module information.
     * <p>
     * For examples see docs for {@link IdeaModule}.
     */
    IdeaModule module

    /**
     * Configures IDEA project information.
     * <p>
     * For examples see docs for {@link IdeaProject}.
     */
    IdeaProject project

    /**
     * Configures IDEA workspace information.
     * <p>
     * For examples see docs for {@link IdeaWorkspace}.
     */
    IdeaWorkspace workspace = new IdeaWorkspace()

    /**
     * Configures IDEA module information.
     * <p>
     * For examples see docs for {@link IdeaModule}.
     *
     * @param closure
     */
    void module(Closure closure) {
        ConfigureUtil.configure(closure, getModule())
    }

    /**
     * Configures IDEA project information.
     * <p>
     * For examples see docs for {@link IdeaProject}.
     *
     * @param closure
     */
    void project(Closure closure) {
        ConfigureUtil.configure(closure, getProject())
    }

    /**
     * Configures IDEA workspace information.
     * <p>
     * For examples see docs for {@link IdeaWorkspace}.
     *
     * @param closure
     */
    void workspace(Closure closure) {
        ConfigureUtil.configure(closure, getWorkspace())
    }

    /**
     * Adds path variables to be used for replacing absolute paths in resulting files (*.iml, etc.).
     * <p>
     * For example see docs for {@link IdeaModule}.
     *
     * @param pathVariables A map with String->File pairs.
     */
    void pathVariables(Map<String, File> pathVariables) {
        assert pathVariables != null
        module.pathVariables.putAll pathVariables
    }
}
