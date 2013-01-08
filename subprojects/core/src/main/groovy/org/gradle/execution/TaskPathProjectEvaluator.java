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

package org.gradle.execution;

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;

/**
 * by Szczepan Faber, created at: 1/8/13
 */
public class TaskPathProjectEvaluator {

    private final ProjectFinderByTaskPath finder;

    public TaskPathProjectEvaluator() {
        this(new ProjectFinderByTaskPath());
    }

    TaskPathProjectEvaluator(ProjectFinderByTaskPath finder) {
        this.finder = finder;
    }

    public void evaluateByPath(ProjectInternal project, String path) {
        if (path.contains(Project.PATH_SEPARATOR)) {
            ProjectInternal foundProject = finder.findProject(path, project);
            foundProject.evaluate();
        } else {
            project.evaluate();
            for (Project sub : project.getSubprojects()) {
                ((ProjectInternal) sub).evaluate();
            }
        }
    }
}
