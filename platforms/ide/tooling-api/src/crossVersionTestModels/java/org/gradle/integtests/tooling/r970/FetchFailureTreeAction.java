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

package org.gradle.integtests.tooling.r970;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.Failure;
import org.gradle.tooling.FetchModelResult;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches {@code modelType} for every project of the root build and its editable builds, capturing for
 * each failed project the root failure's full {@link Failure#getDescription() description} plus a
 * serializable tree of per node messages and causes. Used by the end to end resilient fetch validation
 * to assert the reconstructed failure text and cause structure across the wire.
 */
public class FetchFailureTreeAction implements BuildAction<FetchFailureTreeAction.Result>, Serializable {

    private final Class<?> modelType;

    public FetchFailureTreeAction(Class<?> modelType) {
        this.modelType = modelType;
    }

    @Override
    public Result execute(BuildController controller) {
        GradleBuild gradleBuild = controller.fetch(GradleBuild.class).getModel();
        assert gradleBuild != null;
        Result result = new Result();
        collect(controller, gradleBuild, result);
        for (GradleBuild includedBuild : gradleBuild.getEditableBuilds()) {
            collect(controller, includedBuild, result);
        }
        return result;
    }

    private void collect(BuildController controller, GradleBuild gradleBuild, Result result) {
        for (BasicGradleProject project : gradleBuild.getProjects()) {
            FetchModelResult<?> fetched = controller.fetch(project, modelType);
            if (fetched.getFailures().isEmpty() && fetched.getModel() != null) {
                result.successfullyQueriedProjects.add(project.getName());
            } else {
                result.failedToQueryProjects.add(project.getName());
                Failure failure = fetched.getFailures().iterator().next();
                result.rootDescriptionByProject.put(project.getName(), failure.getDescription());
                result.failureTreeByProject.put(project.getName(), snapshot(failure));
            }
        }
    }

    private static FailureNode snapshot(Failure failure) {
        List<FailureNode> causes = new ArrayList<FailureNode>();
        for (Failure cause : failure.getCauses()) {
            causes.add(snapshot(cause));
        }
        return new FailureNode(failure.getMessage(), causes);
    }

    public static class Result implements Serializable {
        public final List<String> successfullyQueriedProjects = new ArrayList<String>();
        public final List<String> failedToQueryProjects = new ArrayList<String>();
        public final Map<String, String> rootDescriptionByProject = new LinkedHashMap<String, String>();
        public final Map<String, FailureNode> failureTreeByProject = new LinkedHashMap<String, FailureNode>();
    }

    public static class FailureNode implements Serializable {
        public final String message;
        public final List<FailureNode> causes;

        public FailureNode(String message, List<FailureNode> causes) {
            this.message = message;
            this.causes = causes;
        }

        public FailureNode deepest() {
            FailureNode node = this;
            while (!node.causes.isEmpty()) {
                node = node.causes.get(0);
            }
            return node;
        }
    }
}
