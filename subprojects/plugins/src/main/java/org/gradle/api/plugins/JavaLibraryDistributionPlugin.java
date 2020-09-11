/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.Project;
import org.gradle.api.distribution.DistributionContainer;
import org.gradle.api.distribution.plugins.DistributionPlugin;
import org.gradle.api.file.CopySpec;

/**
 * A {@link Plugin} which package a Java project as a distribution including the JAR and runtime dependencies.
 */
public class JavaLibraryDistributionPlugin implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(JavaLibraryPlugin.class);
        project.getPluginManager().apply(DistributionPlugin.class);

        DistributionContainer distributionContainer = (DistributionContainer)project.getExtensions().getByName("distributions");
        distributionContainer.named(DistributionPlugin.MAIN_DISTRIBUTION_NAME).configure(dist -> {
            CopySpec childSpec = project.copySpec();
            childSpec.from(project.getTasks().named(JavaPlugin.JAR_TASK_NAME));
            childSpec.from(project.file("src/dist"));

            CopySpec libSpec = project.copySpec();
            libSpec.into("lib");
            libSpec.from(project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));

            childSpec.with(libSpec);
            dist.getContents().with(childSpec);
        });
    }
}
