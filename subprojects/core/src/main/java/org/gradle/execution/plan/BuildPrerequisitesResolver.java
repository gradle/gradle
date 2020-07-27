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

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.Task;
import org.gradle.api.internal.provider.MissingValueException;
import org.gradle.api.internal.provider.ValueSupplier;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.WorkNodeAction;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class BuildPrerequisitesResolver extends ActionNode implements DependencyResolver {
    private final ValidationAction validationAction;
    private boolean validating = false;

    public BuildPrerequisitesResolver() {
        this(new ValidationAction());
    }

    public BuildPrerequisitesResolver(ValidationAction validationAction) {
        super(validationAction);
        this.validationAction = validationAction;
    }

    @Override
    public boolean resolve(Task task, Object node, Action<? super Node> resolveAction) {
        if (!(node instanceof ValueSupplier.BuildPrerequisite)) {
            return false;
        }

        validationAction.addTaskPrerequisite(task.getName(), (ValueSupplier.BuildPrerequisite) node);
        if (!validating) {
            resolveAction.execute(this);
            validating = true;
        }
        return true;
    }

    @Override
    public boolean attachActionTo(Node value, Action<? super Task> action) {
        return false;
    }

    public static class ValidationAction implements WorkNodeAction {

        private final Map<String, List<ValueSupplier.BuildPrerequisite>> taskPrerequisites = new HashMap<>();

        void addTaskPrerequisite(String taskName, ValueSupplier.BuildPrerequisite validationRequest) {
            taskPrerequisites.computeIfAbsent(taskName, t -> new ArrayList<>()).add(validationRequest);
        }

        @Nullable
        @Override
        public Project getProject() {
            return null;
        }

        @Override
        public void run(NodeExecutionContext context) {
            Set<String> missingPrerequisiteErrors = new HashSet<>();
            taskPrerequisites.forEach((task, taskPrerequisites) ->
                taskPrerequisites.forEach(prerequisite -> ensurePresent(prerequisite, missingPrerequisiteErrors)));
            if (!missingPrerequisiteErrors.isEmpty()) {
                throw new ProjectConfigurationException("Prerequisites for this build could not be resolved.",
                    missingPrerequisiteErrors.stream().map(MissingValueException::new).collect(Collectors.toList()));
            }
        }

        private static void ensurePresent(ValueSupplier.BuildPrerequisite prerequisite, Collection<String> errorsCollector) {
            try {
                prerequisite.ensurePresent();
            } catch (MissingValueException e) {
                errorsCollector.add(e.getMessage());
            }
        }
    }

}
