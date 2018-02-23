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

package org.gradle.play.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Plugin for Play Framework component support. Registers the {@link org.gradle.play.PlayApplicationSpec} component type for the components container.
 */
@Incubating
public class PlayPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(PlayApplicationPlugin.class);
        project.getPluginManager().apply(PlayTestPlugin.class);
        project.getPluginManager().apply(PlayJavaScriptPlugin.class);
        project.getPluginManager().apply(PlayDistributionPlugin.class);
        project.getPluginManager().apply("org.gradle.play-ide");
    }
}
