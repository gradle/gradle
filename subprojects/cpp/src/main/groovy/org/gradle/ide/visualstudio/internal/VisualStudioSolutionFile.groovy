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

package org.gradle.ide.visualstudio.internal

import org.gradle.plugins.ide.internal.generator.AbstractPersistableConfigurationObject

class VisualStudioSolutionFile extends AbstractPersistableConfigurationObject {
    private baseText
    private vsProjects = [] as Set
    private vsConfigurations = []

    protected String getDefaultResourceName() {
        'default.sln'
    }

    void addProjectConfiguration(VisualStudioProjectConfiguration vsConfiguration) {
        vsConfigurations << vsConfiguration
        vsProjects << vsConfiguration.project
    }

    @Override
    void load(InputStream inputStream) throws Exception {
        baseText = inputStream.text
    }

    @Override
    void store(OutputStream outputStream) {
        outputStream << baseText
        vsProjects.each { VisualStudioProject vsProject ->
            outputStream << """
Project("{8BC9CEB8-8B4A-11D0-8D11-00A0C91BC942}") = "${vsProject.getName()}", "${vsProject.getProjectFile()}", "${vsProject.getUuid()}"
EndProject
"""
        }
        outputStream << """
Global
    GlobalSection(SolutionConfigurationPlatforms) = preSolution
        debug|Win32=debug|Win32
    EndGlobalSection
    GlobalSection(ProjectConfigurationPlatforms) = postSolution"""

        vsConfigurations.each { VisualStudioProjectConfiguration vsConfiguration ->
            outputStream << """
                ${vsConfiguration.project.getUuid()}.${vsConfiguration.name}.ActiveCfg = debug|Win32
                ${vsConfiguration.project.getUuid()}.${vsConfiguration.name}.Build.0 = debug|Win32"""
        }

        outputStream << """
    EndGlobalSection
    GlobalSection(SolutionProperties) = preSolution
        HideSolutionNode = FALSE
    EndGlobalSection
EndGlobal
"""
    }
}