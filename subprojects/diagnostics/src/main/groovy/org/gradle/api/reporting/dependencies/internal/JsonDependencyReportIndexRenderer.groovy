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

package org.gradle.api.reporting.dependencies.internal
import groovy.json.JsonBuilder
import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.util.GradleVersion
/**
 * Renderer that emits a JSON tree containing the structure of the data displayed in the index page
 * of an HTML dependency report (list of projects).
 * The structure is the following:
 * <pre>
 *     {
 *         "gradleVersion" : "...",
 *         "generationDate" : "...",
 *         "projects" : [
 *             {
 *                 "path" : "...",
 *                 "name" : "...",
 *                 "description" : "...",
 *                 "file" : "..."
 *             },
 *             ...
 *         ]
 *     }
 * </pre>
 */
class JsonDependencyReportIndexRenderer {

    /**
     * Generates the project dependency report structure
     * @param project the project for which the report must be generated
     * @return the generated JSON, as a String
     */
    String render(Set<Project> projects, Transformer<String, Project> projectToFileName) {
        JsonBuilder json = new JsonBuilder();
        renderProjects(projects, projectToFileName, json);
        return json.toString();
    }

    private void renderProjects(Set<Project> projects,
                                Transformer<String, Project> projectToFileName,
                                JsonBuilder json) {
        List<Project> sortedProjects = sortProjects(projects)
        List jsonProjectList = sortedProjects.collect { project ->
            [
                path: "root" + project.path,
                name: project.name,
                description: project.description,
                file: projectToFileName.transform(project)
            ]
        }
        json gradleVersion : GradleVersion.current().toString(),
             generationDate : new Date().toString(),
             projects : jsonProjectList
    }

    private List<Project> sortProjects(Set<Project> projects) {
        List<Project> sortedProjects = new ArrayList<Project>(projects);
        sortedProjects.sort {
            it.path
        }
        sortedProjects
    }
}
