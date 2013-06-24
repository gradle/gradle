/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildsetup.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.internal.tasks.CommandLineOption
import org.gradle.api.tasks.TaskAction
import org.gradle.buildsetup.plugins.internal.BuildSetupTypeIds
import org.gradle.buildsetup.plugins.internal.ProjectLayoutSetupRegistry

/**
 * Generates a Gradle project structure.
 * */
@Incubating
class SetupBuild extends DefaultTask {
    private String type

    ProjectLayoutSetupRegistry projectLayoutRegistry

    /**
     * The desired type of build to create, defaults to 'pom' if 'pom.xml' is found in project root
     * if no pom.xml is found, it defaults to 'empty'.
     *
     * This property can be set via command-line option '--type'.
     */
    String getType() {
        if (type == null) {
            return project.file("pom.xml").exists() ? BuildSetupTypeIds.POM : BuildSetupTypeIds.EMPTY
        }
        return type
    }

    @TaskAction
    void setupProjectLayout() {
        def type = getType()
        if (!projectLayoutRegistry.supports(type)) {
            throw new GradleException("The requested build setup type '${type}' is not supported. Supported types: ${projectLayoutRegistry.all.collect { "'${it.id}'" }.join(", ")}.")
        }
        projectLayoutRegistry.get(type).generateProject()
    }

    @CommandLineOption(options = "type", description = "Set type of build to create.")
    public void setType(String type) {
        this.type = type;
    }
}
