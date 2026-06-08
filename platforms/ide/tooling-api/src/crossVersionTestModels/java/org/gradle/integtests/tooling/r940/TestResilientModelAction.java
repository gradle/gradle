/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.tooling.r940;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.FetchModelResult;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TestResilientModelAction implements BuildAction<TestResilientModelAction.Result>, Serializable {

    private final Class<?> modelType;
    private final QueryStrategy queryStrategy;

    public TestResilientModelAction(Class<?> modelType, QueryStrategy queryStrategy) {
        this.modelType = modelType;
        this.queryStrategy = queryStrategy;
    }

    @Override
    public Result execute(BuildController controller) {
        GradleBuild gradleBuild = controller.fetch(GradleBuild.class).getModel();
        assert gradleBuild != null;
        List<String> successfulQueriedProjects = new ArrayList<>();
        List<String> failedQueriedProjects = new ArrayList<>();
        if (queryStrategy == QueryStrategy.ROOT_BUILD_FIRST) {
            queryRootBuild(controller, gradleBuild, successfulQueriedProjects, failedQueriedProjects);
            queryEditableBuilds(controller, gradleBuild, successfulQueriedProjects, failedQueriedProjects);
        } else {
            queryEditableBuilds(controller, gradleBuild, successfulQueriedProjects, failedQueriedProjects);
            queryRootBuild(controller, gradleBuild, successfulQueriedProjects, failedQueriedProjects);
        }
        return new Result(successfulQueriedProjects, failedQueriedProjects);
    }

    private void queryRootBuild(BuildController controller, GradleBuild gradleBuild, List<String> successfulQueriedProjects, List<String> failedQueriedProjects) {
        for (BasicGradleProject project : gradleBuild.getProjects()) {
            queryModelForProject(controller, project, successfulQueriedProjects, failedQueriedProjects);
        }
    }

    private void queryEditableBuilds(BuildController controller, GradleBuild gradleBuild, List<String> successfulQueriedProjects, List<String> failedQueriedProjects) {
        for (GradleBuild includedBuild : gradleBuild.getEditableBuilds()) {
            for (BasicGradleProject project : includedBuild.getProjects()) {
                queryModelForProject(controller, project, successfulQueriedProjects, failedQueriedProjects);
            }
        }
    }

    private void queryModelForProject(BuildController controller, BasicGradleProject project, List<String> successfulQueriedProjects, List<String> failedQueriedProjects) {
        FetchModelResult<?> result = controller.fetch(project, modelType);
        if (result.getFailures().isEmpty() && result.getModel() != null) {
            successfulQueriedProjects.add(project.getName());
        } else {
            System.out.println("Failed to query '" + modelType.getSimpleName() + "' for project '" + project.getName() + "'");
            failedQueriedProjects.add(project.getName());
        }
    }

    public enum QueryStrategy {
        ROOT_BUILD_FIRST,
        EDITABLE_BUILDS_FIRST
    }

    public static class Result implements Serializable {
        public final List<String> successfullyQueriedProjects;
        public final List<String> failedToQueryProjects;

        public Result(List<String> successfullyQueriedProjects, List<String> failedToQueryProjects) {
            this.successfullyQueriedProjects = successfullyQueriedProjects;
            this.failedToQueryProjects = failedToQueryProjects;
        }
    }
}
