/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.execution.plan;

import com.google.common.collect.ImmutableCollection;
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.reflect.TypeValidationContext;

public class DefaultNodeValidator implements NodeValidator {
    @Override
    public boolean hasValidationProblems(Node node) {
        if (node instanceof LocalTaskNode) {
            LocalTaskNode taskNode = (LocalTaskNode) node;
            WorkValidationContext validationContext = taskNode.getValidationContext();
            Class<?> taskType = GeneratedSubclasses.unpackType(taskNode.getTask());
            // We don't know whether the task is cacheable or not, so we ignore cacheability problems for scheduling
            TypeValidationContext taskValidationContext = validationContext.forType(taskType, false);
            taskNode.getTaskProperties().validateType(taskValidationContext);
            ImmutableCollection<String> problems = validationContext.getProblems().values();
            problems.forEach(warning -> DeprecationLogger.deprecateBehaviour(warning)
                .withContext("Execution optimizations are disabled due to the failed validation.")
                .willBeRemovedInGradle7()
                .withUserManual("more_about_tasks", "sec:up_to_date_checks")
                .nagUser());
            return !problems.isEmpty();
        } else {
            return false;
        }
    }
}
