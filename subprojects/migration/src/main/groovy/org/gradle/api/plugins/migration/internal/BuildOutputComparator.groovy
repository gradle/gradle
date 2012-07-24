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

package org.gradle.api.plugins.migration.internal

import com.google.common.collect.Sets

import org.gradle.tooling.model.internal.migration.ProjectOutput

class BuildOutputComparator {
    private final BuildComparisonListener listener

    BuildOutputComparator(BuildComparisonListener listener) {
        this.listener = listener
    }

    void compare(ProjectOutput buildOutput1, ProjectOutput buildOutput2) {
        def buildComparison = new BuildComparison()
        buildComparison.build1 = new ComparedBuild(displayName: "source build")
        buildComparison.build2 = new ComparedBuild(displayName: "target build")
        listener.buildComparisonStarted(buildComparison)
        compareProjects(buildOutput1, buildOutput2, buildComparison)
        listener.buildComparisonFinished(buildComparison)
    }

    private void compareProjects(ProjectOutput buildOutput1, ProjectOutput buildOutput2, BuildComparison buildComparison) {
        def projectOutputsByPath1 = getProjectOutputsByPath(buildOutput1)
        def projectOutputsByPath2 = getProjectOutputsByPath(buildOutput2)

        def commonProjectPaths = Sets.intersection(projectOutputsByPath1.keySet(), projectOutputsByPath2.keySet())
        for (path in commonProjectPaths) {
            def projectOutput1 = projectOutputsByPath1[path]
            def projectOutput2 = projectOutputsByPath2[path]
            def projectComparison = new ProjectComparison(parent: buildComparison)
            projectComparison.project1 = new ComparedProject(parent: buildComparison.build1, name: projectOutput1.name, path: path)
            projectComparison.project2 = new ComparedProject(parent: buildComparison.build2, name: projectOutput2.name, path: path)
            buildComparison.projectComparisons << projectComparison
            listener.projectComparisonStarted(projectComparison)
            new ArchivesComparator(projectOutput1, projectOutput2, projectComparison, listener).compareArchives()
            listener.projectComparisonFinished(projectComparison)
        }

        def orphanProjectPaths1 = Sets.difference(projectOutputsByPath1.keySet(), projectOutputsByPath2.keySet())
        for (path in orphanProjectPaths1) {
            def projectOutput = projectOutputsByPath1[path]
            def comparedProject = new ComparedProject(parent: buildComparison.build1, name: projectOutput.name, path: path)
            buildComparison.orphanProjects << comparedProject
            listener.orphanProjectFound(comparedProject)
        }

        def orphanProjectPaths2 = Sets.difference(projectOutputsByPath2.keySet(), projectOutputsByPath1.keySet())
        for (path in orphanProjectPaths2) {
            def projectOutput = projectOutputsByPath2[path]
            def comparedProject = new ComparedProject(parent: buildComparison.build2, name: projectOutput.name, path: path)
            buildComparison.orphanProjects << comparedProject
            listener.orphanProjectFound(comparedProject)
        }
    }

    private Map<String, ProjectOutput> getProjectOutputsByPath(ProjectOutput output, Map<String, ProjectOutput> result = [:]) {
        result.put(output.path, output)
        for (child in output.children) {
            getProjectOutputsByPath(child, result)
        }
        result
    }
}
