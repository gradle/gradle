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
package org.gradle.plugins.eclipse.configurer

import org.gradle.api.Project

/**
 * @author Szczepan Faber, @date: 11.03.11
 */
class EclipseProjectsConfigurer {

    ModuleNameDeduper deduper = new ModuleNameDeduper()

    //TODO SF: this puppy must be genefified with IdeaProjectsConfigurer to reduce duplication! Find out from guys where to put such common code
    void configure(Collection<Project> eclipseProjects) {
        //deduper acts on first-come first-served basis. Therefore it's better to sort projects based on nesting level.
        //This way the 'further' project will get prefixes if necessary
        def sorted = eclipseProjects.sort { it.path.count(":") }
        def eclipseProjectTasks = sorted.collect { it.eclipseProject }
        deduper.dedupeModuleNames(eclipseProjectTasks)
    }
}