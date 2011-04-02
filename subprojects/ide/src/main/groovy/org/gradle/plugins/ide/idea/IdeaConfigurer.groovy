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
package org.gradle.plugins.ide.idea

import org.gradle.api.Project
import org.gradle.plugins.ide.internal.configurer.DeduplicationTarget
import org.gradle.plugins.ide.internal.configurer.ProjectDeduper
import org.gradle.plugins.ide.internal.generator.generator.ConfigurationTarget

/**
 * @author Szczepan Faber, @date 03.03.11
 */
class IdeaConfigurer {

    void configure(Project theProject) {
        def ideaProjects = theProject.rootProject.allprojects.findAll { it.plugins.hasPlugin(IdeaPlugin) }
        new ProjectDeduper().dedupe(ideaProjects, { project ->
            new DeduplicationTarget(project: project, moduleName: project.ideaModule.moduleName, moduleNameSetter: { project.ideaModule.moduleName = it } )
        })

        ideaProjects.each { project ->
            project.tasks.withType(ConfigurationTarget) { it.configureDomainObject() }
        }

        //creation of the domainObject... Necessary for backwards compatibility for now
        theProject.ideaModule.configureDomainObjectNow()
    }
}
