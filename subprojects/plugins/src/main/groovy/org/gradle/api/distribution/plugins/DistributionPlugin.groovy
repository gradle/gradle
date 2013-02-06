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

package org.gradle.api.distribution.plugins

import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.distribution.Distribution
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.distribution.internal.DefaultDistributionContainer
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.reflect.Instantiator

import javax.inject.Inject

/**
 * <p>A {@link Plugin} to package project as a distribution</p>
 *
 * @author scogneau
 *
 */
@Incubating
class DistributionPlugin implements Plugin<Project> {

    static final String DISTRIBUTION_PLUGIN_NAME = "distribution"
    static final String DISTRIBUTION_GROUP = DISTRIBUTION_PLUGIN_NAME
    static final String TASK_DIST_ZIP_NAME = "distZip"

    private DistributionContainer extension
    private Project project
    private Instantiator instantiator

    @Inject
    public DistributionPlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public void apply(Project project) {
        this.project = project
        addPluginExtension()
    }

    void addPluginExtension() {
        extension = project.extensions.create("distributions", DefaultDistributionContainer.class, Distribution.class, instantiator, project.name)
        extension.all{
            dist -> addTask(dist)

        }
        Distribution mainDistribution = extension.create(Distribution.MAIN_DISTRIBUTION_NAME)
        mainDistribution.baseName = project.name
    }

    void addTask(Distribution distribution) {
        def taskName = TASK_DIST_ZIP_NAME
        if (!Distribution.MAIN_DISTRIBUTION_NAME.equals(distribution.name)) {
            taskName = distribution.name + "DistZip"
        }
        def distZipTask = project.tasks.add(taskName, Zip)
        distZipTask.description = "Bundles the project as a distribution."
        distZipTask.group = DISTRIBUTION_GROUP
        distZipTask.conventionMapping.baseName = {
            if (distribution.baseName == null || distribution.baseName.equals("")) {
                throw new GradleException("Distribution baseName must not be null or empty! Check your configuration of the distribution plugin.")
            }
            distribution.baseName
        }
    }
}