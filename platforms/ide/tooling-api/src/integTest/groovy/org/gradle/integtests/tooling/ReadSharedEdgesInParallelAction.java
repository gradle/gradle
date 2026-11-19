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

package org.gradle.integtests.tooling;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.BasicGradleProject;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads every project's {@link SharedEdgesModel} in parallel nested actions and returns the getters that returned null.
 */
public class ReadSharedEdgesInParallelAction implements BuildAction<List<String>> {
    @Override
    public List<String> execute(BuildController controller) {
        List<ReadProjectAction> actions = new ArrayList<>();
        for (BasicGradleProject project : controller.getBuildModel().getProjects()) {
            actions.add(new ReadProjectAction(project));
        }
        List<String> nullReads = new ArrayList<>();
        for (List<String> projectNullReads : controller.run(actions)) {
            nullReads.addAll(projectNullReads);
        }
        return nullReads;
    }

    private static class ReadProjectAction implements BuildAction<List<String>> {
        private final BasicGradleProject project;

        ReadProjectAction(BasicGradleProject project) {
            this.project = project;
        }

        @Override
        public List<String> execute(BuildController controller) {
            List<String> nullReads = new ArrayList<>();
            for (SharedEdgesModel.Dependency dependency : controller.getModel(project, SharedEdgesModel.class).getDependencies()) {
                SharedEdgesModel.Edge edge = dependency.getEdge();
                String from = edge.getFrom();
                String to = edge.getTo();
                if (from == null || to == null) {
                    nullReads.add(project.getPath() + ": from=" + from + " to=" + to);
                }
            }
            return nullReads;
        }
    }
}
