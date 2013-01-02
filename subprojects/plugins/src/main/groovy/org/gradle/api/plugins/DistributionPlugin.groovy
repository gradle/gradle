/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.api.internal.tasks.distribution.DefaultDistributionsContainer
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.distribution.Distribution
import org.gradle.api.tasks.distribution.DistributionsContainer

import javax.inject.Inject;
import org.gradle.internal.reflect.Instantiator;

/**
 * <p>A {@link Plugin} to package project as a distribution</p>
 * 
 * @author scogneau
 *
 */
class DistributionPlugin implements Plugin<Project> {

	static final String DISTRIBUTION_PLUGIN_NAME = "distribution"
	static final String DISTRIBUTION_GROUP = DISTRIBUTION_PLUGIN_NAME
    static final String TASK_DIST_ZIP_NAME = "distZip"

    private DistributionsContainer extension
	private Project project
    private Instantiator instantiator


    @Inject
    public DistributionPlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

	public void apply(Project project) {
		    this.project = project
            project.plugins.apply(JavaPlugin)
			addValidation()
            addPluginExtension()
	}

    void addValidation() {
        project.afterEvaluate {
            extension.all {distribution->
                if (distribution.name == null || distribution.name.empty
                ) {
                    throw new IllegalArgumentException("Distribution name must not be null or empty ! Check your configuration of the distribution plugin.")
                }
            }
        }
    }

	void addPluginExtension() {
        extension = new DefaultDistributionsContainer(Distribution.class,instantiator)
        extension.all {distribution -> addTask(distribution,distribution.name+"DistZip")}
        Distribution distribution = extension.create(Distribution.MAIN_DISTRIBUTION_NAME)
        project.extensions.add("distributions", extension)
       addTask(distribution,TASK_DIST_ZIP_NAME)
	}
	
	void addTask(Distribution distribution,String taskName){
        def distZipTask = project.tasks.add(taskName, Zip)
        distZipTask.description = "Bundles the project as a distribution."
        distZipTask.group = DISTRIBUTION_GROUP
        distZipTask.conventionMapping.baseName = { distribution.name }

    }

 }