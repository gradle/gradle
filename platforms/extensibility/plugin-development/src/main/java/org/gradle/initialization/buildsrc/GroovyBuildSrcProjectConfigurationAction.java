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

package org.gradle.initialization.buildsrc;

import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.JvmConstants;
import org.gradle.api.plugins.GroovyPlugin;
import org.gradle.api.plugins.JavaLibraryPlugin;

public class GroovyBuildSrcProjectConfigurationAction implements BuildSrcProjectConfigurationAction {

    @Override
    public void execute(ProjectInternal project) {
        project.getPluginManager().apply(JavaLibraryPlugin.class);
        project.getPluginManager().apply(GroovyPlugin.class);

        DependencyHandler dependencies = project.getDependencies();
        dependencies.add(JvmConstants.API_CONFIGURATION_NAME, dependencies.gradleApi());
        dependencies.add(JvmConstants.API_CONFIGURATION_NAME, dependencies.localGroovy());
    }
}
