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
 * Fetches the given model for every project of the root build and records, per project path, the messages
 * of the failures attached to its fetch result (including nested causes). Used to assert that resilient sync
 * associates each project only with failures originating from that same project.
 */
public class FetchPerProjectFailuresAction implements BuildAction<FetchPerProjectFailuresAction.Result>, Serializable {

    private final Class<?> modelType;

    public FetchPerProjectFailuresAction(Class<?> modelType) {
        this.modelType = modelType;
    }

    @Override
    public Result execute(BuildController controller) {
        GradleBuild gradleBuild = controller.fetch(GradleBuild.class).getModel();
        assert gradleBuild != null;
        Map<String, List<String>> failureMessagesByProject = new LinkedHashMap<>();
        for (BasicGradleProject project : gradleBuild.getProjects()) {
            failureMessagesByProject.put(project.getPath(), failureMessagesOf(controller.fetch(project, modelType)));
        }
        return new Result(failureMessagesByProject);
    }

    private static List<String> failureMessagesOf(FetchModelResult<?> result) {
        List<String> messages = new ArrayList<>();
        for (Failure failure : result.getFailures()) {
            collectMessages(failure, messages);
        }
        return messages;
    }

    private static void collectMessages(Failure failure, List<String> messages) {
        if (failure == null) {
            return;
        }
        messages.add(failure.getMessage());
        for (Failure cause : failure.getCauses()) {
            collectMessages(cause, messages);
        }
    }

    public static class Result implements Serializable {
        public final Map<String, List<String>> failureMessagesByProject;

        public Result(Map<String, List<String>> failureMessagesByProject) {
            this.failureMessagesByProject = failureMessagesByProject;
        }
    }
}
