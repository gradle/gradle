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
    private BuildComparison buildComparison

    BuildOutputComparator(BuildComparisonListener listener) {
        this.listener = listener
    }

    void compareBuilds(ProjectOutput buildOutput1, ProjectOutput buildOutput2) {
        buildComparison = new BuildComparison()
        buildComparison.build1 = new ComparedBuild(displayName: "source build")
        buildComparison.build2 = new ComparedBuild(displayName: "target build")
        listener.buildComparisonStarted(buildComparison)
        compareProjects(buildOutput1, buildOutput2)
        listener.buildComparisonFinished(buildComparison)
    }

    private void compareProjects(ProjectOutput buildOutput1, ProjectOutput buildOutput2) {
        def projectOutputsByPath1 = getProjectOutputsByPath(buildOutput1)
        def projectOutputsByPath2 = getProjectOutputsByPath(buildOutput2)

        compareCommonProjects(projectOutputsByPath1, projectOutputsByPath2)
        compareOrphanProjects(buildComparison.build1, projectOutputsByPath1, projectOutputsByPath2)
        compareOrphanProjects(buildComparison.build2, projectOutputsByPath2, projectOutputsByPath1)
    }

    private void compareCommonProjects(Map<String, ProjectOutput> projectOutputsByPath1, Map<String, ProjectOutput> projectOutputsByPath2) {
        def commonProjectPaths = Sets.intersection(projectOutputsByPath1.keySet(), projectOutputsByPath2.keySet())
        for (path in commonProjectPaths) {
            def projectOutput1 = projectOutputsByPath1[path]
            def projectOutput2 = projectOutputsByPath2[path]
            def projectComparison = new ProjectComparison(parent: buildComparison)
            projectComparison.project1 = new ComparedProject(parent: buildComparison.build1, name: projectOutput1.name, path: path)
            projectComparison.project2 = new ComparedProject(parent: buildComparison.build2, name: projectOutput2.name, path: path)
            buildComparison.projectComparisons << projectComparison

            listener.projectComparisonStarted(projectComparison)
            new ArchivesComparator(projectComparison, listener).compareArchives(projectOutput1.archives, projectOutput2.archives)
            listener.projectComparisonFinished(projectComparison)
        }
    }

    private void compareOrphanProjects(ComparedBuild build, Map<String, ProjectOutput> projectOutputsByPath, Map<String, ProjectOutput> otherProjectOutputsByPath) {
        def orphanProjectPaths = Sets.difference(projectOutputsByPath.keySet(), otherProjectOutputsByPath.keySet())
        for (path in orphanProjectPaths) {
            def projectOutput = projectOutputsByPath[path]
            def comparedProject = new ComparedProject(parent: build, name: projectOutput.name, path: path)
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
