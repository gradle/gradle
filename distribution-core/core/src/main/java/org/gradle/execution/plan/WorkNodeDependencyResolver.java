/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.WorkNodeAction;

import java.util.IdentityHashMap;
import java.util.Map;

public class WorkNodeDependencyResolver implements DependencyResolver {
    private final Map<WorkNodeAction, ActionNode> nodesForAction = new IdentityHashMap<WorkNodeAction, ActionNode>();

    @Override
    public boolean resolve(Task task, final Object node, Action<? super Node> resolveAction) {
        if (!(node instanceof WorkNodeAction)) {
            return false;
        }

        WorkNodeAction action = (WorkNodeAction) node;
        ActionNode actionNode = actionNodeFor(action);
        resolveAction.execute(actionNode);
        return true;
    }

    private ActionNode actionNodeFor(WorkNodeAction action) {
        ActionNode actionNode = nodesForAction.get(action);
        if (actionNode == null) {
            actionNode = new ActionNode(action);
            nodesForAction.put(action, actionNode);
        }
        return actionNode;
    }

    @Override
    public boolean attachActionTo(Node value, Action<? super Task> action) {
        return false;
    }

}
