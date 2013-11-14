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

package org.gradle.ide.visualstudio.model

import org.gradle.plugins.ide.internal.generator.AbstractPersistableConfigurationObject

class SolutionFile extends AbstractPersistableConfigurationObject {
    private String uuid
    private baseText
    private vsProjects = []

    protected String getDefaultResourceName() {
        'default.sln'
    }

    void setUuid(String uuid) {
        this.uuid = uuid
    }

    void addProject(VisualStudioProject vsProject) {
        vsProjects << vsProject
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
Project("${uuid}") = "${vsProject.name}", "${vsProject.projectFile}", "${vsProject.uuid}"
EndProject
"""
        }
        outputStream << """
Global
    GlobalSection(SolutionConfigurationPlatforms) = preSolution
        debug|Win32=debug|Win32
    EndGlobalSection
EndGlobal
"""


    }
}