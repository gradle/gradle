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
package org.gradle.plugins.ide.idea.internal

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.plugins.ide.idea.GenerateIdeaModule
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.internal.configurer.DeduplicationTarget
import org.gradle.plugins.ide.internal.configurer.ProjectDeduper

@CompileStatic
class IdeaNameDeduper {

    void configureRoot(Project rootProject) {
        def ideaProjects = rootProject.allprojects.findAll { it.plugins.hasPlugin(IdeaPlugin) }
        new ProjectDeduper().dedupe(ideaProjects, { Project project ->
            new DeduplicationTarget(project: project,
                moduleName: ((GenerateIdeaModule) project.tasks.getByName("ideaModule")).module.name,
                updateModuleName: { String moduleName ->
                    ((GenerateIdeaModule) project.tasks.getByName("ideaModule")).module.name = moduleName
                })
        })
    }
}
