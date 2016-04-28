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

package org.gradle.plugin.devel;

import org.gradle.api.Project;

//TODO could be made customizable, so the user is independent of e.g. the project's name
/**
 * Gives access to the coordinates of the main plugin artifact, i.e. the jar containing the plugin implementation classes.
 */
public class PluginArtifactCoordinates {

    private final Project project;

    public PluginArtifactCoordinates(Project project) {
        this.project = project;
    }

    public String getGroup() {
        return project.getGroup() == null ? null : project.getGroup().toString();
    }

    public String getName() {
        return project.getName();
    }

    public String getVersion() {
        return project.getVersion() == null ? null : project.getVersion().toString();
    }
}
