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
package org.gradle.api.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.bundling.Tar


/**
 * <p>A {@link Plugin} which package a project as a Java library.</p>
 *
 * @author Sebastien Cogneau
 */
class LibraryPlugin extends DistPlugin {
    static final String LIBRARY_PLUGIN_NAME = "java-library"
    static final String LIBRARY_GROUP = LIBRARY_PLUGIN_NAME


	
    private LibraryPluginConvention pluginConvention

    void apply(final Project project) {
        this.project = project
        project.plugins.apply(JavaPlugin)

        addPluginConvention()

        configureDistSpec(getDistribution())

        addDistZipTask()
		addDistTarTask()
    }

    protected void addPluginConvention() {
        pluginConvention = new LibraryPluginConvention(project)
        pluginConvention.distributionName = project.name
        project.convention.plugins.library = pluginConvention
    }




    protected CopySpec configureDistSpec(CopySpec distSpec) {
        def jar = project.tasks[JavaPlugin.JAR_TASK_NAME]
  
        distSpec.with {
            from(project.file("src/dist"))

            into("lib") {
                from(jar)
                from(project.configurations.runtime)
            }
        }

        distSpec
    }

	@Override
	protected CopySpec getDistribution() {
		return pluginConvention.distribution;
	}

	@Override
	protected String getGroup() {
		return LIBRARY_GROUP;
	}

	@Override
	protected String getArtefactName() {
		return pluginConvention.distributionName;
	}
}